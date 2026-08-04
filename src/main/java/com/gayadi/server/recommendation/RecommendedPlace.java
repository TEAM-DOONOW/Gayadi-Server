package com.gayadi.server.recommendation;

public record RecommendedPlace(
        String placeId,
        String name,
        String category,
        double score,
        String reason
) {
    public RecommendedPlace {
        if (placeId == null) placeId = "";
        if (name == null) name = "";
        if (category == null) category = "";
        if (reason == null) reason = "";
    }
}
