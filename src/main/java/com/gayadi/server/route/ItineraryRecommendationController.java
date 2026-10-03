package com.gayadi.server.route;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.response.ApiErrorResponse;
import com.gayadi.server.route.dto.request.ItineraryRecommendationRequest;
import com.gayadi.server.route.dto.request.ItinerarySelectionRequest;
import com.gayadi.server.route.dto.response.ItineraryRecommendationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 장소 선택부터 체류시간까지 묶은 여행루트 API입니다.
 * 경로 API와 같이 추천(recommendations)은 계산만 하고, 확정(selections)은 일정을 바꿉니다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "여행루트", description = "하루 장소 순서와 체류. 출발·귀가 교통은 경로")
@SecurityRequirement(name = "bearerAuth")
public class ItineraryRecommendationController {

    private final ItineraryRecommendationService service;

    public ItineraryRecommendationController(ItineraryRecommendationService service) {
        this.service = service;
    }

    @PostMapping("/itinerary-recommendations")
    @Operation(summary = "하루 여행루트 미리보기",
            description = "여행 지역의 장소를 거리·이동수단·남은 시간에 맞춰 순서와 체류시간까지 계산합니다. "
                    + "일정은 바뀌지 않습니다. 이동시간은 직선거리 기반 추정치라 estimated=true입니다. "
                    + "variation을 올리면 다른 전체 루트를 받습니다. "
                    + "확정은 PUT /api/v1/trips/{tripId}/itinerary-selections/{date}입니다. JWT가 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "계산한 하루 여행루트",
                    content = @Content(schema = @Schema(implementation = ItineraryRecommendationResponse.class))),
            @ApiResponse(responseCode = "400", description = "날짜 형식 오류 또는 3~12시간 범위를 벗어난 시간(ROUTE_DAY_RANGE_INVALID)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "여행 지역 없음(ROUTE_REGION_REQUIRED) "
                    + "또는 루트를 만들 장소 부족(ROUTE_CANDIDATES_INSUFFICIENT)",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ItineraryRecommendationResponse recommend(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive long tripId,
            @Valid @RequestBody ItineraryRecommendationRequest request) {
        return service.recommend(userId, tripId, request);
    }

    @PutMapping("/itinerary-selections/{date}")
    @Operation(summary = "여행루트로 해당 날짜 일정 교체",
            description = "미리보기와 같은 조건으로 다시 계산해 expectedPlaceIds와 같으면 "
                    + "해당 날짜의 MAIN 일정 전체를 한 트랜잭션으로 교체합니다. "
                    + "그사이 후보가 바뀌어 순서가 다르면 409 ROUTE_CALCULATION_CHANGED이며 일정은 그대로입니다. "
                    + "JWT가 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "일정에 반영한 하루 여행루트",
                    content = @Content(schema = @Schema(implementation = ItineraryRecommendationResponse.class))),
            @ApiResponse(responseCode = "409", description = "미리보기와 다른 결과(ROUTE_CALCULATION_CHANGED), "
                    + "여행 지역 없음, 장소 부족 또는 수정할 수 없는 여행",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ItineraryRecommendationResponse selection(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive long tripId,
            @Parameter(description = "여행 날짜(yyyy-MM-dd 또는 yyyy.MM.dd)", example = "2026-10-03")
            @PathVariable @Pattern(regexp = AppDateFormat.DATE_PATTERN) String date,
            @Valid @RequestBody ItinerarySelectionRequest request) {
        return service.apply(userId, tripId, date, request);
    }
}
