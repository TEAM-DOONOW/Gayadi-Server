package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.model.PlaceSearchPlan;
import com.gayadi.server.recommendation.model.TourPlaceCandidate;
import com.gayadi.server.recommendation.model.TravelSituation;

import java.util.List;

/** 검색 계획 수립과 후보 선정을 수행하는 추천 언어 모델 계약입니다. */
public interface RecommendationLanguageModel {

    PlaceSearchPlan createSearchPlan(RecommendationContext context);

    PlaceSearchPlan refineSearchPlan(
            RecommendationContext context,
            PlaceSearchPlan previousPlan,
            List<TourPlaceCandidate> candidates);

    CandidateDecision decide(
            RecommendationContext context,
            List<TourPlaceCandidate> candidates);

    record RecommendationContext(
            String purpose,
            String destination,
            String regionCode,
            String sigunguCode,
            String profile,
            List<String> keywords,
            double latitude,
            double longitude,
            int groupSize,
            int limit,
            String targetAt,
            TravelSituation situation,
            TravelSituation.Policy policy
    ) {

        public RecommendationContext {
            purpose = purpose == null ? "PLACE_RECOMMENDATION" : purpose;
            destination = destination == null ? "" : destination;
            regionCode = regionCode == null ? "" : regionCode;
            sigunguCode = sigunguCode == null ? "" : sigunguCode;
            profile = profile == null ? "" : profile;
            keywords = keywords == null ? List.of() : List.copyOf(keywords);
            limit = Math.max(1, Math.min(limit, PlaceRecommendationRequest.MAX_RECOMMENDATIONS));
            targetAt = targetAt == null ? "" : targetAt;
            situation = situation == null ? TravelSituation.empty() : situation;
            policy = policy == null ? situation.policy() : policy;
        }
    }

    record CandidateDecision(
            List<Selection> recommendations,
            String reasoning
    ) {

        public CandidateDecision {
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
            reasoning = reasoning == null ? "" : reasoning;
        }

        public record Selection(
                String placeId,
                String reason,
                Double score
        ) {

            public Selection {
                placeId = placeId == null ? "" : placeId;
                reason = reason == null ? "" : reason;
            }
        }
    }
}
