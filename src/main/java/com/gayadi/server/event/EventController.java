package com.gayadi.server.event;

import com.gayadi.server.event.dto.request.ChangeProposalDecisionRequest;
import com.gayadi.server.event.dto.request.EventObservationRequest;
import com.gayadi.server.event.dto.response.ChangeProposalResponse;
import com.gayadi.server.event.dto.response.EventObservationResponse;
import com.gayadi.server.event.dto.response.EventObservationResult;
import com.gayadi.server.event.command.ChangeProposalDecision;
import com.gayadi.server.event.command.EventObservationCommand;
import com.gayadi.server.event.model.EventType;
import com.gayadi.server.travel.TripService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 현장 상황 등록과 일정 변경 제안 조회·결정 HTTP 요청을 처리합니다. */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "현장 상황", description = "여행 중 확인된 상황과 일정 변경 제안을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService service;
    private final TripService trips;

    public EventController(EventService service, TripService trips) {
        this.service = service;
        this.trips = trips;
    }

    @PostMapping("/event-observations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "현장 상황 등록")
    @ApiResponse(
            responseCode = "201",
            description = "상황 영향도 또는 일정 변경 제안입니다.",
            content = @Content(schema = @Schema(oneOf = {
                    EventObservationResponse.class,
                    ChangeProposalResponse.class
            })))
    public EventObservationResult observe(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody EventObservationRequest request) {
        trips.requireMember(tripId, userId);
        return service.observe(tripId, new EventObservationCommand(
                request.placeId(),
                EventType.from(request.eventType()),
                request.source(),
                request.severity(),
                request.values()));
    }

    @GetMapping("/change-proposals")
    @Operation(summary = "일정 변경 제안 목록")
    @ApiResponse(
            responseCode = "200",
            description = "일정 변경 제안 목록입니다.",
            content = @Content(array = @ArraySchema(schema = @Schema(
                    implementation = ChangeProposalResponse.class))))
    public List<ChangeProposalResponse> proposals(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        trips.requireMember(tripId, userId);
        return service.proposals(tripId, limit, offset);
    }

    @PatchMapping("/change-proposals/{proposalId}")
    @Operation(summary = "일정 변경 제안 처리")
    @ApiResponse(
            responseCode = "200",
            description = "처리한 일정 변경 제안입니다.",
            content = @Content(schema = @Schema(
                    implementation = ChangeProposalResponse.class)))
    public ChangeProposalResponse decide(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long proposalId,
            @Valid @RequestBody ChangeProposalDecisionRequest request) {
        trips.requireMember(tripId, userId);
        return service.decide(tripId, proposalId, new ChangeProposalDecision(
                request.approve(),
                request.selectedOptionKey(),
                request.baseRevisionNo(),
                userId));
    }
}
