package com.gayadi.server.recommendation;

import com.gayadi.server.common.Location;
import com.gayadi.server.place.PlaceRepository;
import com.gayadi.server.place.query.PlaceQueryResult;
import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.dto.response.PlaceRecommendationResponse;
import com.gayadi.server.recommendation.dto.response.RecommendedPlace;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** 외부 추천 Agent를 쓸 수 없을 때 저장된 공개 장소를 거리순으로 추천합니다. */
@Service
public class LocalPlaceRecommendationService {

    private static final int CANDIDATE_LIMIT = 40;

    private final PlaceRepository places;

    public LocalPlaceRecommendationService(PlaceRepository places) {
        this.places = places;
    }

    public PlaceRecommendationResponse recommend(PlaceRecommendationRequest request) {
        Location origin = new Location("추천 기준", request.getLatitude(), request.getLongitude());
        String destination = normalize(request.getDestination());
        List<PlaceQueryResult> candidates = places.findNearbyCandidates(
                null, destination, null, origin, null, CANDIDATE_LIMIT);
        if (candidates.isEmpty() && destination != null) {
            candidates = places.findNearbyCandidates(
                    null, null, null, origin, null, CANDIDATE_LIMIT);
        }

        List<PlaceQueryResult> policyMatches = candidates.stream()
                .filter(place -> !request.getSituation().policy().indoorRequired()
                        || Boolean.TRUE.equals(place.indoor()))
                .toList();
        List<PlaceQueryResult> keywordMatches = policyMatches.stream()
                .filter(place -> matchesKeywords(place, request.getKeywords()))
                .toList();
        List<PlaceQueryResult> selected = (keywordMatches.isEmpty() ? policyMatches : keywordMatches)
                .stream()
                .limit(request.getLimit())
                .toList();

        List<RecommendedPlace> recommendations = IntStream.range(0, selected.size())
                .mapToObj(index -> recommended(selected.get(index), index))
                .toList();
        String reasoning = recommendations.isEmpty()
                ? "조건에 맞는 저장 장소를 찾지 못했어요."
                : "선택한 장소에서 이동하기 가까운 저장 장소 순으로 추천했어요.";
        return new PlaceRecommendationResponse(recommendations, reasoning);
    }

    private RecommendedPlace recommended(PlaceQueryResult place, int index) {
        double score = Math.max(0.5, 0.95 - index * 0.05);
        return new RecommendedPlace(
                String.valueOf(place.id()),
                place.name(),
                place.category().name(),
                score,
                "선택한 장소에서 이어 가기 좋은 가까운 후보예요.",
                "");
    }

    private boolean matchesKeywords(PlaceQueryResult place, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return true;
        }
        String searchable = String.join(" ",
                value(place.name()), value(place.address()), value(place.roadAddress()),
                value(place.basicInfo()), place.category().name()).toLowerCase(Locale.ROOT);
        return keywords.stream()
                .map(this::normalize)
                .filter(java.util.Objects::nonNull)
                .map(keyword -> keyword.toLowerCase(Locale.ROOT))
                .anyMatch(searchable::contains);
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
