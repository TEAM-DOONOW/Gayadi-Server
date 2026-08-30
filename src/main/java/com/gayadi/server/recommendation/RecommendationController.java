package com.gayadi.server.recommendation;

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

@RestController
@RequestMapping("/api/v1/recommendations")
@Tag(name = "추천", description = "여행 성향과 위치에 맞는 장소를 추천합니다.")
@SecurityRequirement(name = "bearerAuth")
public class RecommendationController {

    private final ObjectProvider<RecommendationService> serviceProvider;
    private final ObjectProvider<PlaceRecommendationAgent> agentProvider;
    private final ObjectProvider<SituationResponseAgent> situationAgentProvider;

    public RecommendationController(ObjectProvider<RecommendationService> serviceProvider,
                                    ObjectProvider<PlaceRecommendationAgent> agentProvider,
                                    ObjectProvider<SituationResponseAgent> situationAgentProvider) {
        this.serviceProvider = serviceProvider;
        this.agentProvider = agentProvider;
        this.situationAgentProvider = situationAgentProvider;
    }

    @PostMapping("/places")
    @Operation(summary = "맞춤 장소 추천 Agent 실행",
            description = "TourAPI 후보를 검색하고 여행 성향·현재 위치·상황 정책을 반영해 최종 장소를 선택합니다. "
                    + "APP_AI_ENABLED=true일 때 Agent를 사용하며 Bearer 토큰과 외부 처리 동의가 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "맞춤 장소 추천 성공",
                    content = @Content(schema = @Schema(implementation = PlaceRecommendationResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 외부 처리 미동의",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "로그인 토큰 누락 또는 만료",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "추천 Agent가 비활성화됨",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public PlaceRecommendationResponse recommendPlaces(@Valid @RequestBody PlaceRecommendationRequest request) {
        PlaceRecommendationAgent agent = agentProvider.getIfAvailable();
        if (agent != null) {
            return agent.recommendPlaces(request);
        }
        RecommendationService service = serviceProvider.getIfAvailable();
        if (service == null) {
            throw new BusinessException(RecommendationErrorCode.RECOMMENDATION_UNAVAILABLE);
        }
        return service.recommendPlaces(request);
    }

    @PostMapping("/situations")
    @Operation(summary = "상황 대처 추천",
            description = "날씨·혼잡·교통·대중교통 누락 정보를 반영해 장소 대안과 다음 조치를 제안합니다. "
                    + "APP_AI_ENABLED=true일 때 제공되며 Bearer 토큰과 외부 처리 동의가 필요합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "상황 대처 추천 성공",
                    content = @Content(schema = @Schema(implementation = SituationResponse.class))),
            @ApiResponse(responseCode = "400", description = "요청 검증 실패 또는 외부 처리 미동의",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "로그인 토큰 누락 또는 만료",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "상황 대처 Agent가 비활성화됨",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SituationResponse respondToSituation(@Valid @RequestBody PlaceRecommendationRequest request) {
        SituationResponseAgent agent = situationAgentProvider.getIfAvailable();
        if (agent == null) {
            throw new BusinessException(RecommendationErrorCode.SITUATION_AGENT_UNAVAILABLE);
        }
        return agent.respond(request);
    }
}
