package com.gayadi.server.schedule.dto.request;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.schedule.model.ScheduleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** UpdateScheduleRequest API 요청 데이터를 전달합니다. */
@Schema(name = "UpdateScheduleRequest", description = "일정 항목 수정 정보")
public class UpdateScheduleRequest {
    @Size(max = 200, message = "{validation.schedule.title.size}")
    private String title;

    @Pattern(regexp = AppDateFormat.DATE_PATTERN, message = "{validation.schedule.date.pattern}")
    private String date;

    @Pattern(regexp = AppDateFormat.TIME_PATTERN, message = "{validation.schedule.time.pattern}")
    private String time;

    @Pattern(regexp = AppDateFormat.TIME_PATTERN, message = "{validation.schedule.end-time.pattern}")
    private String endTime;

    private boolean endTimePresent;

    @Size(max = 500, message = "{validation.schedule.memo.size}")
    private String memo;

    private ScheduleType type;

    private Long placeId;

    private boolean placeIdPresent;

    private Boolean isVisited;

    public ScheduleItemService.SchedulePatch patch() {
        return new ScheduleItemService.SchedulePatch(
                title, AppDateFormat.parseDate(date, "일정 날짜"),
                AppDateFormat.parseTime(time, "일정 시각"), type, placeId, placeIdPresent,
                AppDateFormat.parseTime(endTime, "일정 종료 시각"), endTimePresent, memo, isVisited);
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
        this.endTimePresent = true;
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
        this.placeIdPresent = true;
    }

    public Boolean getIsVisited() {
        return isVisited;
    }

    public void setIsVisited(Boolean isVisited) {
        this.isVisited = isVisited;
    }

    public void setVisited(Boolean visited) {
        this.isVisited = visited;
    }
}
