package com.gayadi.server.schedule.dto.request;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.schedule.model.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** CreateScheduleRequest API 요청 데이터를 전달합니다. */
@Schema(name = "CreateScheduleRequest", description = "새 일정 항목 생성 정보")
public class CreateScheduleRequest {

    @NotBlank(message = "{validation.schedule.title.required}")
    @Size(max = 200, message = "{validation.schedule.title.size}")
    private String title;

    @NotBlank(message = "{validation.schedule.date.required}")
    @Pattern(regexp = AppDateFormat.DATE_PATTERN, message = "{validation.schedule.date.pattern}")
    private String date;

    @NotBlank(message = "{validation.schedule.time.required}")
    @Pattern(regexp = AppDateFormat.TIME_PATTERN, message = "{validation.schedule.time.pattern}")
    private String time;

    @Pattern(regexp = AppDateFormat.TIME_PATTERN, message = "{validation.schedule.end-time.pattern}")
    private String endTime;

    @Size(max = 500, message = "{validation.schedule.memo.size}")
    private String memo;

    @NotNull(message = "{validation.schedule.type.required}")
    private ScheduleType type;

    private Long placeId;

    public ScheduleItemService.ScheduleCommand command() {
        return new ScheduleItemService.ScheduleCommand(
                title,
                AppDateFormat.parseDate(date, "일정 날짜"),
                AppDateFormat.parseTime(time, "일정 시각"),
                type,
                placeId,
                AppDateFormat.parseTime(endTime, "일정 종료 시각"),
                memo);
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public ScheduleType getType() {
        return type;
    }

    public void setType(ScheduleType type) {
        this.type = type;
    }

    public Long getPlaceId() {
        return placeId;
    }

    public void setPlaceId(Long placeId) {
        this.placeId = placeId;
    }
}
