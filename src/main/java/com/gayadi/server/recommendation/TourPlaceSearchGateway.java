package com.gayadi.server.recommendation;

import java.util.List;

public interface TourPlaceSearchGateway {

    List<TourPlaceCandidate> search(PlaceSearchPlan plan, SearchContext context);

    record SearchContext(
            String regionCode,
            String sigunguCode,
            double latitude,
            double longitude,
            TravelSituation.Policy policy
    ) {
    }
}
