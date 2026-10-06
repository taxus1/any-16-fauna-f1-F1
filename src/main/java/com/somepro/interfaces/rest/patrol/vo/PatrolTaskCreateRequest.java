package com.somepro.interfaces.rest.patrol.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 派发巡护任务请求（VO，用户接口层）。
 *
 * 编号不用填：服务端按 PT-YYYY-NNNN 生成，一个号只归一条任务。
 * 站在运行、点在册、计划日期今天往后等规则由应用层 / 领域层把关。
 */
public record PatrolTaskCreateRequest(
        @NotNull(message = "所属监测站不能为空") Long stationId,
        @NotNull(message = "监测点不能为空") Long siteId,
        @NotBlank(message = "巡护类型不能为空") String patrolType,
        @NotNull(message = "计划日期不能为空") LocalDate plannedDate,
        String executor) implements Serializable {
}
