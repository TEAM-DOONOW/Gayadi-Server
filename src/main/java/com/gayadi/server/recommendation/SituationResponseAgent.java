package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.dto.response.PlaceRecommendationResponse;
import com.gayadi.server.recommendation.dto.response.SituationResponse;
import com.gayadi.server.recommendation.model.TravelSituation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 날씨·혼잡·교통·대중교통 누락 상황에 대응하는 Agent입니다. */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class SituationResponseAgent {

    private final PlaceRecommendationAgent recommendations;

    public SituationResponseAgent(PlaceRecommendationAgent recommendations) {
        this.recommendations = recommendations;
    }

    public SituationResponse respond(PlaceRecommendationRequest request) {
        request.setPurpose(PlaceRecommendationRequest.PURPOSE_SITUATION_RESPONSE);
        TravelSituation situation = request.getSituation();
        TravelSituation.Policy policy = situation.policy();
        PlaceRecommendationResponse places = recommendations.recommendPlaces(request);
        boolean routeRecalculationRequired = policy.transitDisrupted();
        String nextAction;
        if (routeRecalculationRequired) {
            nextAction = "대중교통 상황이 바뀌었으므로 대체 경로를 다시 계산해야 합니다.";
        } else if (policy.indoorRequired()) {
            nextAction = "날씨 영향으로 실내 장소 중심의 대안을 확인하세요.";
        } else if (policy.avoidCrowded()) {
            nextAction = "혼잡도가 높은 장소를 피하는 대안을 확인하세요.";
        } else {
            nextAction = "현재 일정의 후보를 다시 확인하세요.";
        }
        return new SituationResponse(policy.summary(), routeRecalculationRequired,
                nextAction, places);
    }
}
