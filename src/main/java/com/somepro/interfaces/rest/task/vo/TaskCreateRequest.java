package com.somepro.interfaces.rest.task.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 派发巡护任务请求（VO，用户接口层）。
 *
 * taskNo 可空：空则由服务端按 PT-YYYY-NNNN 生成。
 * 站必须在运行、点必须在册、同点同日不能有未完成任务、计划日期不能早于今天（应用层校验）。
 */
public record TaskCreateRequest(
        String taskNo,
        @NotNull(message = "所属监测站不能为空") Long stationId,
        @NotNull(message = "监测点不能为空") Long siteId,
        @NotBlank(message = "巡护类型不能为空") String patrolType,
        @NotNull(message = "计划日期不能为空") LocalDate plannedDate,
        String executor) implements Serializable {
}
