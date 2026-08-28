package com.gayadi.server.coordination;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/date-coordination")
@Tag(name = "날짜 조율", description = "그룹 여행 참여자의 가능한 날짜 제출과 여행 기간 확정")
@SecurityRequirement(name = "bearerAuth")
public class DateCoordinationController {

    private final DateCoordinationService service;

    public DateCoordinationController(DateCoordinationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "날짜 조율 현황")
    public DateCoordinationResponse get(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        return service.get(userId, tripId);
    }

    @PutMapping("/availability/current")
    @Operation(summary = "내 가능한 날짜 제출",
            description = "인증된 참여자 자신의 이전 제출을 새 날짜 목록으로 교체합니다.")
    public DateCoordinationResponse submit(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody DateAvailabilityRequest request) {
        return service.submit(userId, tripId, request.dates());
    }

    @PutMapping("/finalized-dates")
    @Operation(summary = "여행 기간 확정",
            description = "여행 소유자가 모든 참여자의 공통 가능 날짜 안에서 연속된 기간을 확정합니다.")
    @ApiResponse(responseCode = "409", description = "미제출 참여자가 있거나 공통 날짜가 아님")
    public DateCoordinationResponse finalizeDates(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody FinalizeTripDatesRequest request) {
        return service.finalizeDates(userId, tripId, request.startDate(), request.endDate());
    }
}
