package com.gayadi.server.recommendation;

import java.util.List;

public record PlaceRecommendationResponse(
        List<RecommendedPlace> recommendations,
        String reasoning
) {
    public PlaceRecommendationResponse {
        if (recommendations == null) recommendations = List.of();
        if (reasoning == null) reasoning = "";
    }
}
