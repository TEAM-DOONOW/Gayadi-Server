package com.gayadi.server.recommendation;

import java.util.Map;

public record SituationResponse(
        String situationSummary,
        boolean routeRecalculationRequired,
        String nextAction,
        PlaceRecommendationResponse placeRecommendations,
        Map<String, Object> changeProposal
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
