package com.gayadi.server.schedule;

import com.gayadi.server.schedule.dto.request.CreateScheduleRequest;
import com.gayadi.server.schedule.dto.request.ScheduleOrderRequest;
import com.gayadi.server.schedule.dto.request.UpdateScheduleRequest;
import com.gayadi.server.schedule.dto.response.ScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 여행 일정과 계획 관련 HTTP 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "일정", description = "앱에서 직접 편집하는 여행 일정을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class ScheduleItemController {
    private final ScheduleItemService service;

    public ScheduleItemController(ScheduleItemService service) {
        this.service = service;
    }

    @GetMapping("/schedules")
    @Operation(summary = "일정 목록")
    @ApiResponse(responseCode = "200", description = "여행의 날짜별 일정 목록입니다.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleResponse.class))))
    public List<ScheduleResponse> list(@AuthenticationPrincipal Long userId, @PathVariable long tripId) {
        return service.list(userId, tripId);
    }

    @PostMapping("/schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "일정 추가")
    @ApiResponse(responseCode = "201", description = "추가한 일정입니다.",
            content = @Content(schema = @Schema(implementation = ScheduleResponse.class)))
    public ScheduleResponse create(@AuthenticationPrincipal Long userId, @PathVariable long tripId,
                                   @Valid @RequestBody CreateScheduleRequest request) {
        return service.create(userId, tripId, request.command());
    }

    @PatchMapping("/schedules/{scheduleId}")
    @Operation(summary = "일정 수정")
    @ApiResponse(responseCode = "200", description = "수정한 일정입니다.",
            content = @Content(schema = @Schema(implementation = ScheduleResponse.class)))
    public ScheduleResponse update(@AuthenticationPrincipal Long userId, @PathVariable long tripId,
                                   @PathVariable long scheduleId,
                                   @Valid @RequestBody UpdateScheduleRequest request) {
        return service.update(userId, tripId, scheduleId, request.patch());
    }

    @DeleteMapping("/schedules/{scheduleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "일정 삭제")
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long tripId,
                       @PathVariable long scheduleId) {
        service.delete(userId, tripId, scheduleId);
    }

    @PatchMapping("/schedule-orders")
    @Operation(summary = "일정 순서 변경")
    @ApiResponse(responseCode = "200", description = "바뀐 순서대로 정렬한 일정 목록입니다.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ScheduleResponse.class))))
    public List<ScheduleResponse> reorder(@AuthenticationPrincipal Long userId, @PathVariable long tripId,
                                          @Valid @RequestBody ScheduleOrderRequest request) {
        return service.reorder(userId, tripId, request.getScheduleIds());
    }
}
