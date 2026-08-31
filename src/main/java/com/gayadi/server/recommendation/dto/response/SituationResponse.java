package com.gayadi.server.recommendation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** 날씨·혼잡·교통 상황에 대한 대응 결과와 변경 제안을 반환합니다. */
@Schema(description = "날씨·혼잡·교통 상황 대처 Agent 응답")
public record SituationResponse(
        @Schema(description = "현재 상황과 적용 정책 요약")
        String situationSummary,

        @Schema(description = "기존 경로를 무효화하고 재계산해야 하는지")
        boolean routeRecalculationRequired,

        @Schema(description = "사용자가 다음에 할 행동")
        String nextAction,

        @Schema(description = "현재 상황에 맞는 대체 장소")
        PlaceRecommendationResponse placeRecommendations,

        @Schema(description = "진행 중 여행에 생성된 승인 대기 변경안. 일반 추천에서는 빈 객체")
        SituationChangeProposalResponse changeProposal
) {

    public SituationResponse(String situationSummary,
                             boolean routeRecalculationRequired,
                             String nextAction,
                             PlaceRecommendationResponse placeRecommendations) {
        this(situationSummary, routeRecalculationRequired, nextAction,
                placeRecommendations, SituationChangeProposalResponse.empty());
    }

    public SituationResponse {
        situationSummary = situationSummary == null ? "" : situationSummary;
        nextAction = nextAction == null ? "" : nextAction;
        placeRecommendations = placeRecommendations == null
                ? new PlaceRecommendationResponse(java.util.List.of(), "")
                : placeRecommendations;
        changeProposal = changeProposal == null
                ? SituationChangeProposalResponse.empty()
                : changeProposal;
    }

    public SituationResponse withChangeProposal(SituationChangeProposalResponse proposal) {
        return new SituationResponse(situationSummary, routeRecalculationRequired,
                nextAction, placeRecommendations, proposal);
    }
}
