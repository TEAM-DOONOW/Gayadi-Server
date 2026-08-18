package com.gayadi.server.schedule;

import com.gayadi.server.config.ApiSuccessSchemas;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/plans")
@Tag(name = "일정", description = "여행 일정을 만들고 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class PlanController {

    private final PlanService service;
    private final TripService trips;

    public PlanController(PlanService service, TripService trips) {
        this.service = service;
        this.trips = trips;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "자동 일정 생성")
    @ApiResponse(responseCode = "201", description = "여행 성향을 반영해 만든 일정입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Plan.class)))
    public Map<String, Object> generate(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        trips.requireMember(tripId, userId);
        return service.generate(tripId);
    }

    @GetMapping
    @Operation(summary = "자동 일정 조회")
    @ApiResponse(responseCode = "200", description = "저장된 자동 일정입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Plan.class)))
    public Map<String, Object> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        trips.requireMember(tripId, userId);
        return service.get(tripId);
    }
}
