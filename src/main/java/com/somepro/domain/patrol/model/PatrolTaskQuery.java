package com.somepro.domain.patrol.model;

/**
 * 巡护任务翻单条件（领域值对象，不可变）。
 *
 * 站 / 点 / 类型 / 状态 / 计划日期随意拼，全为 null 时翻整份任务名单。
 * includeCancelled=false 时强制不带已取消任务（默认名单场景）；
 * status 显式传 CANCELLED 时 includeCancelled=true（按状态翻，已取消也要翻得出来）。
 */
public record PatrolTaskQuery(Long stationId, Long siteId, String patrolType,
                              String status, java.time.LocalDate plannedDate,
                              boolean includeCancelled) {

    public static PatrolTaskQuery of(Long stationId, Long siteId, String patrolType,
                                     String status, java.time.LocalDate plannedDate) {
        boolean includeCancelled = PatrolTask.STATUS_CANCELLED.equals(status);
        return new PatrolTaskQuery(stationId, siteId, patrolType, status, plannedDate, includeCancelled);
    }
}
