package com.somepro.infrastructure.persistence.patrol;

import cn.hutool.core.util.IdUtil;
import com.somepro.common.exception.BizException;
import com.somepro.domain.patrol.model.PatrolTask;
import com.somepro.infrastructure.persistence.patrol.converter.PatrolTaskPoConverter;
import com.somepro.infrastructure.persistence.patrol.po.PatrolTaskPO;
import com.somepro.infrastructure.persistence.support.BizNoGenerator;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 巡护任务「派单 / 改派」事务执行器（基础设施层）。
 *
 * 独立成一个 bean 的原因：{@code @Transactional} 走 Spring 代理，必须从另一个 bean 调用才生效
 * （同类内部自调用绕过代理）。仓储适配器在 boundedElastic 线程上同步调它，
 * 事务全程跑在同一线程、同一 JDBC 连接上 —— 这是命名锁 GET_LOCK 与后续查重 / 落库
 * 必须共连接的前提。
 *
 * 并发闸口（表上没有「点+天」唯一索引、建表脚本不能碰，用 MySQL 命名锁兜底）：
 * - 派单按「点」加锁 patrol:site:{siteId}，锁内重查同点同日未结束任务再落库，
 *   两个人前后脚往同一点同一天派，只会放一条进来；
 * - 改派可能换点 / 改天，旧坑、新坑两把锁都拿（排序获取避免互锁），锁内重查占位（排除自己）。
 */
@Component
public class PatrolTaskTxExecutor {

    /** 同点锁等待秒数：抢不到说明同一点正在派单，稍等即放；超时转业务异常，不甩底层错。 */
    private static final int LOCK_WAIT_SECONDS = 5;

    /** 编号前缀：PT-年-（如 PT-2026-） */
    private static final String NO_PREFIX = "PT-";

    private final PatrolTaskMapper taskMapper;

    public PatrolTaskTxExecutor(PatrolTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    /** 派单落库（锁内查重 + 编号生成重试），由 {@code PatrolTaskRepositoryImpl} 调用。 */
    @Transactional(rollbackFor = Exception.class)
    public PatrolTask insertWithSiteLock(PatrolTask task) {
        String lock = lockName(task.getSiteId());
        acquire(lock);
        try {
            if (countOpenSlots(task.getSiteId(), task.getPlannedDate(), null) > 0) {
                throw new BizException("该监测点当天已有未结束的巡护任务，不能重复派发");
            }
            String prefix = NO_PREFIX + LocalDate.now().getYear() + "-";
            return BizNoGenerator.insertWithRetry(
                    () -> taskMapper.selectMaxSeq(prefix, prefix.length() + 1),
                    prefix,
                    no -> doInsert(task, no));
        } finally {
            release(lock);
        }
    }

    /** 改派落库（旧坑/新坑双锁 + 锁内重查占位），由 {@code PatrolTaskRepositoryImpl} 调用。 */
    @Transactional(rollbackFor = Exception.class)
    public PatrolTask updateWithSlotLocks(PatrolTask changed) {
        PatrolTaskPO current = taskMapper.selectById(changed.getId());
        if (current == null) {
            throw new BizException("巡护任务不存在");
        }
        List<String> locks = new java.util.ArrayList<>(List.of(
                lockName(current.getSiteId()), lockName(changed.getSiteId())));
        locks = locks.stream().distinct().sorted().toList();
        for (String lock : locks) {
            acquire(lock);
        }
        try {
            // 改派后落的新坑：同点同日还有别的（id 不同）未结束任务就挡住
            if (countOpenSlots(changed.getSiteId(), changed.getPlannedDate(), changed.getId()) > 0) {
                throw new BizException("该监测点当天已有未结束的巡护任务，不能重复派发");
            }
            PatrolTaskPO po = PatrolTaskPoConverter.toPo(changed);
            taskMapper.updateById(po);
            return PatrolTaskPoConverter.toDomain(po);
        } finally {
            // 获取按排序，释放反序
            for (int i = locks.size() - 1; i >= 0; i--) {
                release(locks.get(i));
            }
        }
    }

    /** 同点同日未结束（待执行 / 执行中）任务数；已完成 / 已取消 / 已撤单都不占位。 */
    public long countOpenSlots(Long siteId, LocalDate plannedDate, Long excludeId) {
        return taskMapper.selectCount(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<PatrolTaskPO>lambdaQuery()
                .eq(PatrolTaskPO::getSiteId, siteId)
                .eq(PatrolTaskPO::getPlannedDate, plannedDate)
                .in(PatrolTaskPO::getStatus,
                        PatrolTask.STATUS_PENDING, PatrolTask.STATUS_IN_PROGRESS)
                .ne(excludeId != null, PatrolTaskPO::getId, excludeId));
    }

    private PatrolTask doInsert(PatrolTask task, String taskNo) {
        task.setTaskNo(taskNo);
        PatrolTaskPO po = PatrolTaskPoConverter.toPo(task);
        po.setId(IdUtil.getSnowflakeNextId());
        taskMapper.insert(po);
        return PatrolTaskPoConverter.toDomain(po);
    }

    private void acquire(String lock) {
        Integer result = taskMapper.getLock(lock, LOCK_WAIT_SECONDS);
        if (result == null || result != 1) {
            throw new BizException("同一监测点正在派发任务，请稍后再试");
        }
    }

    private void release(String lock) {
        try {
            taskMapper.releaseLock(lock);
        } catch (RuntimeException ignored) {
            // 连接随事务关闭后命名锁自动释放，释放失败不影响主流程，不掩盖原异常
        }
    }

    private static String lockName(Long siteId) {
        return "patrol:site:" + siteId;
    }
}
