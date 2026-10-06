package com.somepro.infrastructure.persistence.task.converter;

import com.somepro.domain.task.model.PatrolTask;
import com.somepro.infrastructure.persistence.task.po.PatrolTaskPO;

/**
 * PatrolTaskPO（表）↔ PatrolTask（领域）转换器（基础设施层）。
 *
 * obs_count / abnormal_count / started_at / finished_at 不属于本模块管理的字段，
 * 领域对象不承载；PO 侧保持 null，落库时走表默认值（MyBatis-Plus 非空策略跳过）。
 */
public final class PatrolTaskPoConverter {

    private PatrolTaskPoConverter() {
    }

    public static PatrolTaskPO toPo(PatrolTask domain) {
        PatrolTaskPO po = new PatrolTaskPO();
        po.setId(domain.getId());
        po.setTaskNo(domain.getTaskNo());
        po.setStationId(domain.getStationId());
        po.setSiteId(domain.getSiteId());
        po.setPatrolType(domain.getPatrolType());
        po.setPlannedDate(domain.getPlannedDate());
        po.setExecutor(domain.getExecutor());
        po.setStatus(domain.getStatus());
        po.setDelFlag(domain.getDelFlag());
        po.setCreateBy(domain.getCreateBy());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateBy(domain.getUpdateBy());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PatrolTask toDomain(PatrolTaskPO po) {
        PatrolTask domain = new PatrolTask();
        domain.setId(po.getId());
        domain.setTaskNo(po.getTaskNo());
        domain.setStationId(po.getStationId());
        domain.setSiteId(po.getSiteId());
        domain.setPatrolType(po.getPatrolType());
        domain.setPlannedDate(po.getPlannedDate());
        domain.setExecutor(po.getExecutor());
        domain.setStatus(po.getStatus());
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
