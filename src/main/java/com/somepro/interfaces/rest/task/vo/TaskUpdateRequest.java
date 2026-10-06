package com.somepro.interfaces.rest.task.vo;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 修改巡护任务请求（VO，用户接口层）：字段都可空，传啥改啥。
 * 换站/换点要过「站在运行、点在册」校验，改完后的（点, 计划日期）不能撞别的未完成任务。
 */
public record TaskUpdateRequest(
        Long stationId,
        Long siteId,
        String patrolType,
        LocalDate plannedDate,
        String executor) implements Serializable {
}
