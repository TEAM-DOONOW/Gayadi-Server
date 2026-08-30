package com.gayadi.server.schedule;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.dto.ApiResponseMapper;
import com.gayadi.server.common.dto.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "일정", description = "앱에서 직접 편집하는 여행 일정을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleItemController {

    private final ScheduleItemService service;
    private final ApiResponseMapper mapper;

    public ScheduleItemController(ScheduleItemService service, ApiResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/schedules")
    @Operation(summary = "일정 목록")
    @ApiResponse(responseCode = "200", description = "여행의 날짜별 일정 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiResponses.Schedule.class))))
    public List<ApiResponses.Schedule> list(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        return mapper.toDtoList(service.list(userId, tripId), ApiResponses.Schedule.class);
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "일정 추가")
    @ApiResponse(responseCode = "201", description = "추가한 일정입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Schedule.class)))
    public ApiResponses.Schedule create(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody CreateScheduleRequest request) {
        return mapper.toDto(service.create(userId, tripId, request.command()), ApiResponses.Schedule.class);
    }

    @PatchMapping("/schedules/{scheduleId}")
    @Operation(summary = "일정 수정")
    @ApiResponse(responseCode = "200", description = "수정한 일정입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Schedule.class)))
    public ApiResponses.Schedule update(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long scheduleId,
            @Valid @RequestBody UpdateScheduleRequest request) {
        return mapper.toDto(service.update(userId, tripId, scheduleId, request.patch()), ApiResponses.Schedule.class);
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "일정 삭제")
    public void delete(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long scheduleId) {
        service.delete(userId, tripId, scheduleId);
    }

    @PatchMapping("/schedule-orders")
    @Operation(summary = "일정 순서 변경")
    @ApiResponse(responseCode = "200", description = "바뀐 순서대로 정렬한 일정 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiResponses.Schedule.class))))
    public List<ApiResponses.Schedule> reorder(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody ScheduleOrderRequest request) {
        return mapper.toDtoList(
                service.reorder(userId, tripId, request.getScheduleIds()),
                ApiResponses.Schedule.class);
    }

    public static class CreateScheduleRequest {
        @NotBlank
        @Size(max = 200)
        private String title;
        @NotBlank
        @Pattern(regexp = AppDateFormat.DATE_PATTERN,
                message = "일정 날짜는 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.")
        private String date;
        @NotBlank
        @Pattern(regexp = AppDateFormat.TIME_PATTERN,
                message = "일정 시각은 HH:mm 형식이어야 합니다.")
        private String time;
        @Pattern(regexp = AppDateFormat.TIME_PATTERN,
                message = "일정 종료 시각은 HH:mm 형식이어야 합니다.")
        private String endTime;
        @Size(max = 500)
        private String memo;
        @NotNull
        private ScheduleItemService.ScheduleType type;
        private Long placeId;

        ScheduleItemService.ScheduleCommand command() {
            return new ScheduleItemService.ScheduleCommand(
                    title, AppDateFormat.parseDate(date, "일정 날짜"),
                    AppDateFormat.parseTime(time, "일정 시각"), type, placeId,
                    AppDateFormat.parseTime(endTime, "일정 종료 시각"), memo);
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) { this.endTime = endTime; }
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
        public ScheduleItemService.ScheduleType getType() { return type; }
        public void setType(ScheduleItemService.ScheduleType type) { this.type = type; }
        public Long getPlaceId() { return placeId; }
        public void setPlaceId(Long placeId) { this.placeId = placeId; }

    }

    /**
     * 일정 수정은 안드로이드에서 방문 여부만 바꾸는 경우도 있어 모든 값을 선택값으로 받는다.
     * placeId와 endTime은 빠진 경우와 명시적으로 null을 보낸 경우를 구분해 연결·종료 시각을 해제할 수 있다.
     */
    public static class UpdateScheduleRequest {
        @Size(max = 200)
        private String title;
        @Pattern(regexp = AppDateFormat.DATE_PATTERN,
                message = "일정 날짜는 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.")
        private String date;
        @Pattern(regexp = AppDateFormat.TIME_PATTERN,
                message = "일정 시각은 HH:mm 형식이어야 합니다.")
        private String time;
        @Pattern(regexp = AppDateFormat.TIME_PATTERN,
                message = "일정 종료 시각은 HH:mm 형식이어야 합니다.")
        private String endTime;
        private boolean endTimePresent;
        @Size(max = 500)
        private String memo;
        private ScheduleItemService.ScheduleType type;
        private Long placeId;
        private boolean placeIdPresent;
        private Boolean isVisited;

        ScheduleItemService.SchedulePatch patch() {
            return new ScheduleItemService.SchedulePatch(
                    title, AppDateFormat.parseDate(date, "일정 날짜"),
                    AppDateFormat.parseTime(time, "일정 시각"),
                    type, placeId, placeIdPresent,
                    AppDateFormat.parseTime(endTime, "일정 종료 시각"),
                    endTimePresent, memo, isVisited);
        }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String endTime) {
            this.endTime = endTime;
            this.endTimePresent = true;
        }
        public String getMemo() { return memo; }
        public void setMemo(String memo) { this.memo = memo; }
        public ScheduleItemService.ScheduleType getType() { return type; }
        public void setType(ScheduleItemService.ScheduleType type) { this.type = type; }
        public Long getPlaceId() { return placeId; }
        public void setPlaceId(Long placeId) {
            this.placeId = placeId;
            this.placeIdPresent = true;
        }
        public Boolean getIsVisited() { return isVisited; }
        public void setIsVisited(Boolean isVisited) { this.isVisited = isVisited; }
        public void setVisited(Boolean visited) { this.isVisited = visited; }

    }

    public static class ScheduleOrderRequest {
        @NotEmpty
        private List<@NotNull Long> scheduleIds;

        public List<Long> getScheduleIds() { return scheduleIds; }
        public void setScheduleIds(List<Long> scheduleIds) { this.scheduleIds = scheduleIds; }
    }
}
