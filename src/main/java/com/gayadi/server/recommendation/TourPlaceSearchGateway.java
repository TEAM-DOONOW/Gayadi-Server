package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.model.PlaceSearchPlan;
import com.gayadi.server.recommendation.model.TourPlaceCandidate;
import com.gayadi.server.recommendation.model.TravelSituation;

import java.util.List;

/** 검색 계획에 따라 관광 장소 후보를 조회하는 Gateway 계약입니다. */
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
