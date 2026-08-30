package com.gayadi.server.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

@Schema(description = "날씨·혼잡·교통 상황 대처 Agent 응답")
public record SituationResponse(
        @Schema(description = "현재 상황과 적용 정책 요약") String situationSummary,
        @Schema(description = "기존 경로를 무효화하고 재계산해야 하는지") boolean routeRecalculationRequired,
        @Schema(description = "사용자가 다음에 할 행동") String nextAction,
        @Schema(description = "현재 상황에 맞는 대체 장소") PlaceRecommendationResponse placeRecommendations,
        @Schema(description = "진행 중 여행에 생성된 승인 대기 변경안. 일반 추천에서는 빈 객체") Map<String, Object> changeProposal
) {

    public SituationResponse(String situationSummary,
                             boolean routeRecalculationRequired,
                             String nextAction,
                             PlaceRecommendationResponse placeRecommendations) {
        this(situationSummary, routeRecalculationRequired, nextAction,
                placeRecommendations, Map.of());
    }

    public SituationResponse {
        situationSummary = situationSummary == null ? "" : situationSummary;
        nextAction = nextAction == null ? "" : nextAction;
        placeRecommendations = placeRecommendations == null
                ? new PlaceRecommendationResponse(java.util.List.of(), "")
                : placeRecommendations;
        changeProposal = changeProposal == null ? Map.of() : changeProposal;
    }

    public SituationResponse withChangeProposal(Map<String, Object> proposal) {
        return new SituationResponse(situationSummary, routeRecalculationRequired,
                nextAction, placeRecommendations, proposal);
    }
}
