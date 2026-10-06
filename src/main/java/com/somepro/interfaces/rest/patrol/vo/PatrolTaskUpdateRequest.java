package com.somepro.interfaces.rest.patrol.vo;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 改派巡护任务请求（VO，用户接口层）：字段都可空，传啥改啥。
 * 只有待执行任务能改；换站 / 换点重新过派发校验。
 */
public record PatrolTaskUpdateRequest(
        Long stationId,
        Long siteId,
        String patrolType,
        LocalDate plannedDate,
        String executor) implements Serializable {
}
