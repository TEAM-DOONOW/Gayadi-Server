package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.dto.response.PlaceRecommendationResponse;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

/** 맞춤 장소 추천 HTTP 요청을 처리합니다. */
@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "추천", description = "맞춤 장소 추천 Agent. APP_AI_ENABLED=true 필요")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final ObjectProvider<PlaceRecommendationAgent> agentProvider;

    public RecommendationController(ObjectProvider<PlaceRecommendationAgent> agentProvider) {
        this.agentProvider = agentProvider;
    }

    @PostMapping("/places")
    @Operation(summary = "맞춤 장소 추천 Agent 실행",
            description = "TourAPI 후보를 검색하고 여행 성향·현재 위치·상황 정책을 반영해 최종 장소를 선택합니다. "
                    + "Bearer JWT와 `externalProcessingConsent: true`가 필요합니다. "
                    + "APP_AI_ENABLED=false이면 503 RECOMMENDATION_UNAVAILABLE입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "맞춤 장소 추천 성공",
                    content = @Content(schema = @Schema(implementation = PlaceRecommendationResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 외부 처리 미동의",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "로그인 토큰 누락 또는 만료",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "추천 Agent가 꺼져 있습니다. RECOMMENDATION_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PlaceRecommendationResponse recommendPlaces(@Valid @RequestBody PlaceRecommendationRequest request) {
        PlaceRecommendationAgent agent = agentProvider.getIfAvailable();
        if (agent == null) {
            throw new BusinessException(RecommendationErrorCode.RECOMMENDATION_UNAVAILABLE);
        }
        return agent.recommendPlaces(request);
    }
}
