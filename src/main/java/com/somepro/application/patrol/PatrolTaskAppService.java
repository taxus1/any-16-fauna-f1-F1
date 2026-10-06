package com.somepro.application.patrol;

import com.somepro.common.exception.BizException;
import com.somepro.domain.patrol.model.PatrolTask;
import com.somepro.domain.patrol.model.PatrolTaskQuery;
import com.somepro.domain.patrol.repository.PatrolTaskRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.domain.site.model.MonitorSite;
import com.somepro.domain.site.repository.MonitorSiteRepository;
import com.somepro.domain.station.model.MonitorStation;
import com.somepro.domain.station.repository.MonitorStationRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 巡护任务应用层：编排任务用例（派发、改派、详情、取消、条件翻单）。
 *
 * 派单看两头：
 * - 站得实实在在存在、且在运行（ACTIVE），停用 / 关闭都不再往那儿派；
 * - 点得在册（ACTIVE），停测 / 撤掉的点不派；点所属站与目标站对不上也不派。
 * 「同点同日只能挂一条没走完的任务」与计划日期不早于今天分别由仓储事务锁、领域对象兜底。
 */
@Service
public class PatrolTaskAppService {

    /** 业务时区：与建表脚本、Jackson（GMT+8）保持一致。 */
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final PatrolTaskRepository taskRepository;
    private final MonitorStationRepository stationRepository;
    private final MonitorSiteRepository siteRepository;

    public PatrolTaskAppService(PatrolTaskRepository taskRepository,
                                MonitorStationRepository stationRepository,
                                MonitorSiteRepository siteRepository) {
        this.taskRepository = taskRepository;
        this.stationRepository = stationRepository;
        this.siteRepository = siteRepository;
    }

    /** 派发巡护任务：默认待执行，编号由仓储层按 PT-YYYY-NNNN 生成。 */
    public Mono<PatrolTask> dispatch(Long stationId, Long siteId, String patrolType,
                                     LocalDate plannedDate, String executor) {
        LocalDate today = LocalDate.now(ZONE);
        return Mono.fromCallable(() -> PatrolTask.dispatch(
                        stationId, siteId, patrolType, plannedDate, executor, today))
                .flatMap(task -> requireDispatchable(stationId, siteId).then(taskRepository.create(task)));
    }

    /**
     * 改派：传啥改啥。只有待执行任务能改；换站 / 换点都重新过一遍两头校验；
     * 改到同一点同一天且那儿已有别的未结束任务，由仓储锁内重查挡住。
     */
    public Mono<PatrolTask> revise(Long id, Long stationId, Long siteId, String patrolType,
                                   LocalDate plannedDate, String executor) {
        LocalDate today = LocalDate.now(ZONE);
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("巡护任务不存在")))
                .flatMap(task -> {
                    // 先做不变量校验：非待执行直接挡，避免查了一圈账才报状态错
                    task.revise(stationId, siteId, patrolType, plannedDate, executor, today);
                    Long targetStationId = stationId != null ? stationId : task.getStationId();
                    Long targetSiteId = siteId != null ? siteId : task.getSiteId();
                    return requireDispatchable(targetStationId, targetSiteId)
                            .then(taskRepository.update(task));
                });
    }

    public Mono<PatrolTask> detail(Long id) {
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("巡护任务不存在")));
    }

    /** 取消：待执行 / 执行中 -> 已取消；已取消幂等，已完成拒绝。账留着，不物理删。 */
    public Mono<PatrolTask> cancel(Long id) {
        return taskRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("巡护任务不存在")))
                .flatMap(task -> {
                    task.cancel();
                    // 取消是状态流转，不重查同点同日占位（坑位本该让出来）
                    return taskRepository.updateStatus(task);
                });
    }

    /** 条件分页：站 / 点 / 类型 / 状态 / 计划日期随意拼，全空翻整份（默认不带已取消）。 */
    public Mono<PageResult<PatrolTask>> pageTasks(int pageNum, int pageSize, Long stationId,
                                                  Long siteId, String patrolType,
                                                  String status, LocalDate plannedDate) {
        PatrolTaskQuery query = PatrolTaskQuery.of(stationId, siteId, patrolType, status, plannedDate);
        return taskRepository.page(pageNum, pageSize, query);
    }

    /**
     * 派发两头校验：站存在且在运行；点存在且在册，且归属站与目标站一致。
     */
    private Mono<MonitorSite> requireDispatchable(Long stationId, Long siteId) {
        return stationRepository.findById(stationId)
                .switchIfEmpty(Mono.error(new BizException("所属监测站不存在")))
                .flatMap(this::requireRunningStation)
                .then(siteRepository.findById(siteId)
                        .switchIfEmpty(Mono.error(new BizException("监测点不存在"))))
                .flatMap(site -> {
                    if (!MonitorSite.STATUS_ACTIVE.equals(site.getStatus())) {
                        return Mono.error(new BizException("监测点已停测，不能派发巡护任务"));
                    }
                    if (!stationId.equals(site.getStationId())) {
                        return Mono.error(new BizException("监测点不属于该监测站"));
                    }
                    return Mono.just(site);
                });
    }

    private Mono<MonitorStation> requireRunningStation(MonitorStation station) {
        if (MonitorStation.STATUS_CLOSED.equals(station.getStatus())) {
            return Mono.error(new BizException("监测站已关闭，不能派发巡护任务"));
        }
        if (MonitorStation.STATUS_SUSPENDED.equals(station.getStatus())) {
            return Mono.error(new BizException("监测站已停用，不能派发巡护任务"));
        }
        return Mono.just(station);
    }
}
