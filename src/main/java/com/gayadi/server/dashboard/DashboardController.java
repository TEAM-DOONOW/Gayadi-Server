package com.gayadi.server.dashboard;

import com.gayadi.server.dashboard.dto.response.DashboardResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 여행 홈 통합 정보 조회 HTTP 요청을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/trips/{tripId}/dashboard")
@Tag(name = "여행 홈", description = "여행, 참여자, 일정과 변경 제안을 한 번에 조회합니다.")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(
            summary = "여행 홈 조회",
            description = "저장된 여행, 참여자, 일정과 변경 제안만 제공합니다. 확인되지 않은 날씨나 혼잡 정보는 넣지 않습니다.")
    @ApiResponse(responseCode = "200", description = "여행 홈을 구성하는 자료입니다.",
            content = @Content(schema = @Schema(implementation = DashboardResponse.class)))
    public DashboardResponse dashboard(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive(message = "여행 번호는 1 이상이어야 합니다.") long tripId) {
        return service.dashboard(userId, tripId);
    }
}
