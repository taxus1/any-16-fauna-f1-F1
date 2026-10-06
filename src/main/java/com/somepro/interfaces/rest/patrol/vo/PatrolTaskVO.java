package com.somepro.interfaces.rest.patrol.vo;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 巡护任务对外返回对象（VO，用户接口层）—— 不可变 record。编号 taskNo 必带。
 */
public record PatrolTaskVO(Long id, String taskNo, Long stationId, Long siteId,
                           String patrolType, LocalDate plannedDate, String executor,
                           String status, LocalDateTime createTime) implements Serializable {
}
