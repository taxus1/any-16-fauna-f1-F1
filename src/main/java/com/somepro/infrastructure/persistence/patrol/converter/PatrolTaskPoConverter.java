package com.somepro.infrastructure.persistence.patrol.converter;

import com.somepro.domain.patrol.model.PatrolTask;
import com.somepro.infrastructure.persistence.patrol.po.PatrolTaskPO;

/**
 * PatrolTaskPO（表）↔ PatrolTask（领域）转换器（基础设施层）。
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
        po.setObsCount(domain.getObsCount());
        po.setAbnormalCount(domain.getAbnormalCount());
        po.setStartedAt(domain.getStartedAt());
        po.setFinishedAt(domain.getFinishedAt());
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
        domain.setObsCount(po.getObsCount());
        domain.setAbnormalCount(po.getAbnormalCount());
        domain.setStartedAt(po.getStartedAt());
        domain.setFinishedAt(po.getFinishedAt());
        domain.setDelFlag(po.getDelFlag());
        domain.setCreateBy(po.getCreateBy());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateBy(po.getUpdateBy());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
