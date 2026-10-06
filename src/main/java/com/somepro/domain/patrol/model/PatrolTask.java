package com.somepro.domain.patrol.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

/**
 * 巡护任务聚合根（领域层）：站点点位立住后往下派的巡护工单。
 *
 * 纯领域对象：只描述业务与不变量，不带任何持久化注解（表映射在基础设施层的 PatrolTaskPO）。
 *
 * 业务规则：
 * - 巡护类型 ROUTINE 常规 / SPECIAL 专项 / EMERGENCY 应急；
 * - 状态 PENDING 待执行 / IN_PROGRESS 执行中 / DONE 已完成 / CANCELLED 已取消，新派默认待执行；
 * - 计划日期不能早于今天（今天往后）；
 * - 派发时站必须运行（ACTIVE）、点必须在册（ACTIVE），同一点同一天不能挂两条没走完的任务
 *   （待执行 / 执行中占位，已完成 / 已取消不占位）——这些校验要查别的账，由应用层编排；
 * - 录错了只能在「待执行」阶段改；取消只对没走完的任务有意义，重复取消幂等。
 *
 * 编号 taskNo 由仓储层在落库时分配（PT-2026-0001 式），领域对象只持有不生成。
 */
@Getter
@Setter
public class PatrolTask extends BaseEntity {

    /** 类型：常规 */
    public static final String TYPE_ROUTINE = "ROUTINE";
    /** 类型：专项 */
    public static final String TYPE_SPECIAL = "SPECIAL";
    /** 类型：应急 */
    public static final String TYPE_EMERGENCY = "EMERGENCY";

    /** 状态：待执行 */
    public static final String STATUS_PENDING = "PENDING";
    /** 状态：执行中 */
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    /** 状态：已完成 */
    public static final String STATUS_DONE = "DONE";
    /** 状态：已取消 */
    public static final String STATUS_CANCELLED = "CANCELLED";

    private static final Set<String> TYPES = Set.of(TYPE_ROUTINE, TYPE_SPECIAL, TYPE_EMERGENCY);

    private Long id;

    /** 巡护任务编号（如 PT-2026-0001），全局唯一，一个号只归一条任务 */
    private String taskNo;

    /** 派给哪个监测站 */
    private Long stationId;

    /** 去哪个监测点 */
    private Long siteId;

    /** 巡护类型：ROUTINE / SPECIAL / EMERGENCY */
    private String patrolType;

    /** 计划日期（今天往后，不能填昨天） */
    private LocalDate plannedDate;

    /** 执行人 */
    private String executor;

    /** 状态：PENDING / IN_PROGRESS / DONE / CANCELLED */
    private String status;

    /** 该任务下观测记录条数（完成时汇总回写，本模块只读不写） */
    private Integer obsCount;

    /** 该任务下异常个体条数（完成时汇总回写，本模块只读不写） */
    private Integer abnormalCount;

    /** 开始执行时刻（执行流程回写，本模块只读不写） */
    private java.time.LocalDateTime startedAt;

    /** 完成时刻（执行流程回写，本模块只读不写） */
    private java.time.LocalDateTime finishedAt;

    /**
     * 工厂方法：派发巡护任务，保证初始不变量（默认待执行）。
     *
     * @param today 今天的日期（由应用层按业务时区传入，避免领域层直接依赖系统默认时区）
     */
    public static PatrolTask dispatch(Long stationId, Long siteId, String patrolType,
                                      LocalDate plannedDate, String executor, LocalDate today) {
        PatrolTask task = new PatrolTask();
        task.assignStation(stationId);
        task.assignSite(siteId);
        task.changeType(patrolType);
        task.planOn(plannedDate, today);
        task.setExecutor(blankToNull(executor));
        task.setStatus(STATUS_PENDING);
        task.obsCount = 0;
        task.abnormalCount = 0;
        return task;
    }

    public void assignStation(Long stationId) {
        if (stationId == null) {
            throw new BizException("所属监测站不能为空");
        }
        this.stationId = stationId;
    }

    public void assignSite(Long siteId) {
        if (siteId == null) {
            throw new BizException("监测点不能为空");
        }
        this.siteId = siteId;
    }

    public void changeType(String patrolType) {
        if (patrolType == null || !TYPES.contains(patrolType)) {
            throw new BizException("巡护类型非法，仅支持 ROUTINE/SPECIAL/EMERGENCY");
        }
        this.patrolType = patrolType;
    }

    /** 计划日期不能早于今天。 */
    public void planOn(LocalDate plannedDate, LocalDate today) {
        if (plannedDate == null) {
            throw new BizException("计划日期不能为空");
        }
        if (today != null && plannedDate.isBefore(today)) {
            throw new BizException("计划日期不能早于今天");
        }
        this.plannedDate = plannedDate;
    }

    /**
     * 录错了改派：传入的字段才改（null 表示不动）。
     * 只有「待执行」的任务能改 —— 已经动身（执行中）、完成、取消的都不能再改。
     */
    public void revise(Long stationId, Long siteId, String patrolType,
                       LocalDate plannedDate, String executor, LocalDate today) {
        if (!STATUS_PENDING.equals(this.status)) {
            throw new BizException("只有待执行的任务才能修改，当前状态：" + this.status);
        }
        if (stationId != null) {
            assignStation(stationId);
        }
        if (siteId != null) {
            assignSite(siteId);
        }
        if (patrolType != null) {
            changeType(patrolType);
        }
        if (plannedDate != null) {
            planOn(plannedDate, today);
        }
        if (executor != null) {
            // 空串视为清空执行人
            this.executor = blankToNull(executor);
        }
    }

    /**
     * 取消：待执行 / 执行中 -> 已取消。已完成的任务账不能撤；
     * 已经取消的再取消是幂等空操作（状态保持 CANCELLED）。
     */
    public void cancel() {
        if (STATUS_CANCELLED.equals(this.status)) {
            return;
        }
        if (STATUS_DONE.equals(this.status)) {
            throw new BizException("已完成的任务不能取消");
        }
        this.status = STATUS_CANCELLED;
    }

    /** 该任务是否还占着「同点同日」的坑：待执行 / 执行中算占位。 */
    public boolean occupiesSlot() {
        return STATUS_PENDING.equals(status) || STATUS_IN_PROGRESS.equals(status);
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
