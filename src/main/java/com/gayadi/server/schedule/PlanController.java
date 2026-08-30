package com.gayadi.server.schedule;

import com.gayadi.server.common.dto.ApiResponseMapper;
import com.gayadi.server.common.dto.ApiResponses;
import com.gayadi.server.travel.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/plans")
@Tag(name = "일정", description = "여행 일정을 만들고 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class PlanController {

    private final PlanService service;
    private final TripService trips;
    private final ApiResponseMapper mapper;

    public PlanController(PlanService service, TripService trips, ApiResponseMapper mapper) {
        this.service = service;
        this.trips = trips;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "자동 일정 생성")
    @ApiResponse(responseCode = "201", description = "여행 성향을 반영해 만든 일정입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Plan.class)))
    public ApiResponses.Plan generate(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        trips.requireMember(tripId, userId);
        return mapper.toDto(service.generate(tripId), ApiResponses.Plan.class);
    }

    @GetMapping
    @Operation(summary = "자동 일정 조회")
    @ApiResponse(responseCode = "200", description = "저장된 자동 일정입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Plan.class)))
    public ApiResponses.Plan get(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        trips.requireMember(tripId, userId);
        return mapper.toDto(service.get(tripId), ApiResponses.Plan.class);
    }
}
