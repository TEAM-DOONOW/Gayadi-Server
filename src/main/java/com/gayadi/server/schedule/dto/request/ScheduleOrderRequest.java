package com.gayadi.server.schedule.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** ScheduleOrderRequest API 요청 데이터를 전달합니다. */
@Schema(name = "ScheduleOrderRequest", description = "일정 항목 정렬 순서")
public class ScheduleOrderRequest {
    @NotEmpty(message = "{validation.schedule.order.required}")
    private List<@NotNull(message = "{validation.schedule.order.item-required}") Long> scheduleIds;

    public List<Long> getScheduleIds() {
        return scheduleIds;
    }

    public void setScheduleIds(List<Long> scheduleIds) {
        this.scheduleIds = scheduleIds;
    }
}
