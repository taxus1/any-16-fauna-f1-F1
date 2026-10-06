package com.somepro.infrastructure.persistence.patrol;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.patrol.model.PatrolTask;
import com.somepro.domain.patrol.model.PatrolTaskQuery;
import com.somepro.domain.patrol.repository.PatrolTaskRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.patrol.converter.PatrolTaskPoConverter;
import com.somepro.infrastructure.persistence.patrol.po.PatrolTaskPO;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 巡护任务仓储适配器（基础设施层）。
 *
 * 约定同其他模块：所有 DB 调用经 {@link #blocking} 桥接到 boundedElastic；
 * PO 与领域对象在本类经 {@link PatrolTaskPoConverter} 互转，不泄到外层。
 *
 * 派单 / 改派的「查重 + 落库」事务在 {@link PatrolTaskTxExecutor} 里：
 * @Transactional 必须跨 bean 调用才走 Spring 代理，且整个事务要在同一条 JDBC 连接上
 * （MySQL 命名锁是连接级的），所以这里在阻塞线程上同步调用执行器，而不是包一层 Mono。
 */
@Repository
public class PatrolTaskRepositoryImpl implements PatrolTaskRepository {

    private final PatrolTaskMapper taskMapper;
    private final PatrolTaskTxExecutor txExecutor;

    public PatrolTaskRepositoryImpl(PatrolTaskMapper taskMapper, PatrolTaskTxExecutor txExecutor) {
        this.taskMapper = taskMapper;
        this.txExecutor = txExecutor;
    }

    @Override
    public Mono<PatrolTask> create(PatrolTask task) {
        return blocking(() -> txExecutor.insertWithSiteLock(task));
    }

    @Override
    public Mono<PatrolTask> update(PatrolTask task) {
        return blocking(() -> txExecutor.updateWithSlotLocks(task));
    }

    @Override
    public Mono<PatrolTask> updateStatus(PatrolTask task) {
        return blocking(() -> {
            PatrolTaskPO po = PatrolTaskPoConverter.toPo(task);
            taskMapper.updateById(po);
            return PatrolTaskPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PatrolTask> findById(Long id) {
        return blocking(() -> {
            PatrolTaskPO po = taskMapper.selectById(id);
            return po == null ? null : PatrolTaskPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<PageResult<PatrolTask>> page(int pageNum, int pageSize, PatrolTaskQuery query) {
        return this.<PageResult<PatrolTask>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<PatrolTaskPO> wrapper = Wrappers.<PatrolTaskPO>lambdaQuery()
                        .eq(query.stationId() != null, PatrolTaskPO::getStationId, query.stationId())
                        .eq(query.siteId() != null, PatrolTaskPO::getSiteId, query.siteId())
                        .eq(hasText(query.patrolType()), PatrolTaskPO::getPatrolType, query.patrolType())
                        .eq(hasText(query.status()), PatrolTaskPO::getStatus, query.status())
                        // 默认名单不带已取消；显式按 CANCELLED 翻时才放行
                        .ne(!query.includeCancelled(), PatrolTaskPO::getStatus, PatrolTask.STATUS_CANCELLED)
                        .eq(query.plannedDate() != null, PatrolTaskPO::getPlannedDate, query.plannedDate())
                        .orderByDesc(PatrolTaskPO::getPlannedDate)
                        .orderByAsc(PatrolTaskPO::getId);
                List<PatrolTaskPO> rows = taskMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<PatrolTask> content = rows.stream()
                        .map(PatrolTaskPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                // 分页参数靠 ThreadLocal 传递，必须清理，否则污染线程池里的下一次调用
                PageHelper.clearPage();
            }
        });
    }

    @Override
    public Mono<Long> countOpenSlots(Long siteId, LocalDate plannedDate, Long excludeId) {
        return blocking(() -> txExecutor.countOpenSlots(siteId, plannedDate, excludeId));
    }

    @Override
    public Mono<Void> softDelete(Long id) {
        return blocking(() -> {
            // @TableLogic：UPDATE t_patrol_task SET del_flag = 1 WHERE id = ? AND del_flag = 0
            taskMapper.deleteById(id);
            return Boolean.TRUE;
        }).then();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 阻塞 DB 调用 → 响应式链路的桥接器：先取 Reactor Context 里的操作人，
     * 再切到 boundedElastic 执行 JDBC，操作人放进 AuditContextHolder 供审计填充。
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
