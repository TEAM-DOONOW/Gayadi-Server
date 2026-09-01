package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.dto.response.PlaceRecommendationResponse;
import com.gayadi.server.recommendation.dto.response.RecommendedPlace;
import com.gayadi.server.recommendation.model.PlaceSearchPlan;
import com.gayadi.server.recommendation.model.TourPlaceCandidate;
import com.gayadi.server.recommendation.model.TravelSituation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceRecommendationAgentTest {

    @Test
    void removesOutdoorCandidatesBeforeTheLanguageModelMakesAChoice() {
        FakeGateway gateway = new FakeGateway(List.of(
                candidate("outdoor", "울산 대왕암공원", "ATTRACTION", false, 2.0),
                candidate("indoor", "울산박물관", "CULTURE", true, 4.0)));
        PlaceRecommendationAgent agent = new PlaceRecommendationAgent(
                new FakeLanguageModel("indoor"), gateway);

        PlaceRecommendationRequest request = request(
                new TravelSituation(
                        new TravelSituation.Weather("RAIN", "비", 15.0, 90, 23.0, 2.0),
                        TravelSituation.Congestion.empty(),
                        TravelSituation.Transit.empty()));

        PlaceRecommendationResponse response = agent.recommendPlaces(request);

        assertThat(response.recommendations()).extracting(RecommendedPlace::placeId)
                .containsExactly("indoor");
        assertThat(gateway.lastPolicy.indoorRequired()).isTrue();
    }

    @Test
    void missedTransitProducesARecommendationWithTheTransitSituationInContext() {
        FakeGateway gateway = new FakeGateway(List.of(
                candidate("near", "울산역 인근 문화시설", "CULTURE", true, 1.0)));
        FakeLanguageModel model = new FakeLanguageModel("near");
        PlaceRecommendationAgent agent = new PlaceRecommendationAgent(model, gateway);

        PlaceRecommendationRequest request = request(new TravelSituation(
                TravelSituation.Weather.empty(),
                TravelSituation.Congestion.empty(),
                new TravelSituation.Transit(true, "BUS", 20, "버스를 놓쳤습니다.", "")));

        PlaceRecommendationResponse response = agent.recommendPlaces(request);

        assertThat(response.recommendations()).hasSize(1);
        assertThat(model.lastContext.policy().transitDisrupted()).isTrue();
        assertThat(model.lastContext.limit()).isEqualTo(5);
    }

    @Test
    void persistsOnlyUniqueAllowedSelectionsAfterTheLanguageModelDecision() {
        TourPlaceCandidate first = candidate("first", "첫 장소", "CULTURE", true, 1.0);
        TourPlaceCandidate second = candidate("second", "둘째 장소", "CULTURE", true, 2.0);
        TourPlaceCandidate third = candidate("third", "셋째 장소", "CULTURE", true, 3.0);
        FakeGateway gateway = new FakeGateway(List.of(first, second, third));
        FakeLanguageModel model = new FakeLanguageModel(List.of(
                new RecommendationLanguageModel.CandidateDecision.Selection(
                        "missing", "후보에 없는 ID", 1.0),
                new RecommendationLanguageModel.CandidateDecision.Selection(
                        "second", "두 번째를 선택", 0.9),
                new RecommendationLanguageModel.CandidateDecision.Selection(
                        "second", "중복 선택", 0.8),
                new RecommendationLanguageModel.CandidateDecision.Selection(
                        "first", "첫 번째를 선택", Double.NaN)));
        CapturingSnapshotWriter writer = new CapturingSnapshotWriter();
        PlaceRecommendationAgent agent = new PlaceRecommendationAgent(model, gateway, writer);
        PlaceRecommendationRequest request = request(TravelSituation.empty());
        request.setLimit(2);

        PlaceRecommendationResponse response = agent.recommendPlaces(request);

        assertThat(writer.saved).extracting(TourPlaceCandidate::placeId)
                .containsExactly("second", "first");
        assertThat(response.recommendations()).extracting(RecommendedPlace::sourcePlaceId)
                .containsExactly("second", "first");
        assertThat(response.recommendations()).extracting(RecommendedPlace::placeId)
                .containsExactly("102", "101");
        assertThat(response.recommendations().get(1).score()).isFinite();
    }

    @Test
    void fallbackPlanUsesOnePageAndRequestOwnedRegionAndCoordinates() {
        PlaceSearchPlan fallback = PlaceSearchPlan.fallback(
                "울산", "31", "", List.of("박물관"), TravelSituation.empty().policy());
        assertThat(fallback.queries().getFirst().maxPages()).isEqualTo(1);
        assertThat(fallback.queries()).extracting(PlaceSearchPlan.Query::operation)
                .containsExactly("KEYWORD", "AREA");

        PlaceSearchPlan.Query modelQuery = new PlaceSearchPlan.Query(
                List.of("박물관"), "LOCATION", List.of("14"),
                "MODEL_REGION", "MODEL_CITY", "0", "0", 50_000, 9);
        PlaceSearchPlan.Query bounded = modelQuery.withContext("31", "7", 35.5, 129.3);

        assertThat(bounded.regionCode()).isEqualTo("31");
        assertThat(bounded.sigunguCode()).isEqualTo("7");
        assertThat(bounded.mapX()).isEqualTo("129.3");
        assertThat(bounded.mapY()).isEqualTo("35.5");
        assertThat(bounded.radiusMeters()).isEqualTo(20_000);
        assertThat(bounded.maxPages()).isEqualTo(3);
    }

    @Test
    void modelSearchQueryNormalizesAndBoundsRepeatedKeywords() {
        PlaceSearchPlan.Query query = new PlaceSearchPlan.Query(
                List.of(" 박물관 ", "", "박물관", "미술관", "전시관", "문화관", "과학관"),
                " keyword ", List.of("14", " 14 ", "", "12"),
                "11", "", null, null, 15_000, 1);

        assertThat(query.operation()).isEqualTo("KEYWORD");
        assertThat(query.keywords())
                .containsExactly("박물관", "미술관", "전시관", "문화관", "과학관");
        assertThat(query.contentTypeIds()).containsExactly("14", "12");
    }

    @Test
    void fallsBackToServerPlanningAndRankingWhenTheLanguageModelFails() {
        FakeGateway gateway = new FakeGateway(List.of(
                candidate("near", "가까운 박물관", "CULTURE", true, 1.0),
                candidate("far", "먼 박물관", "CULTURE", true, 10.0)));
        RecommendationLanguageModel failing = new RecommendationLanguageModel() {
            @Override
            public PlaceSearchPlan createSearchPlan(RecommendationContext context) {
                throw new IllegalStateException("model unavailable");
            }

            @Override
            public PlaceSearchPlan refineSearchPlan(RecommendationContext context,
                                                    PlaceSearchPlan previousPlan,
                                                    List<TourPlaceCandidate> candidates) {
                throw new IllegalStateException("model unavailable");
            }

            @Override
            public CandidateDecision decide(RecommendationContext context,
                                            List<TourPlaceCandidate> candidates) {
                throw new IllegalStateException("model unavailable");
            }
        };
        PlaceRecommendationAgent agent = new PlaceRecommendationAgent(failing, gateway);
        PlaceRecommendationRequest request = request(TravelSituation.empty());
        request.setLimit(1);

        PlaceRecommendationResponse response = agent.recommendPlaces(request);

        assertThat(response.recommendations()).extracting(RecommendedPlace::placeId)
                .containsExactly("near");
        assertThat(response.recommendations().getFirst().reason())
                .contains("검색 조건");
    }

    private PlaceRecommendationRequest request(TravelSituation situation) {
        PlaceRecommendationRequest request = new PlaceRecommendationRequest();
        request.setDestination("울산");
        request.setRegionCode("31");
        request.setProfile("도시 문화와 활동적인 여행을 좋아합니다.");
        request.setKeywords(List.of("문화시설"));
        request.setLatitude(35.5384);
        request.setLongitude(129.3114);
        request.setGroupSize(5);
        request.setExternalProcessingConsent(true);
        request.setSituation(situation);
        return request;
    }

    private TourPlaceCandidate candidate(String id, String name, String category,
                                         boolean indoor, double distanceKm) {
        return new TourPlaceCandidate(id, name, category, "14", "울산", 35.5, 129.3,
                indoor, distanceKm, name + " 설명", "");
    }

    private static final class FakeGateway implements TourPlaceSearchGateway {
        private final List<TourPlaceCandidate> candidates;
        private TravelSituation.Policy lastPolicy;

        private FakeGateway(List<TourPlaceCandidate> candidates) {
            this.candidates = candidates;
        }

        @Override
        public List<TourPlaceCandidate> search(PlaceSearchPlan plan, SearchContext context) {
            lastPolicy = context.policy();
            return candidates;
        }
    }

    private static final class FakeLanguageModel implements RecommendationLanguageModel {
        private final List<CandidateDecision.Selection> selections;
        private RecommendationContext lastContext;

        private FakeLanguageModel(String selectedId) {
            this(List.of(new CandidateDecision.Selection(
                    selectedId, "상황과 사용자 조건에 맞는 후보입니다.", 0.9)));
        }

        private FakeLanguageModel(List<CandidateDecision.Selection> selections) {
            this.selections = selections;
        }

        @Override
        public PlaceSearchPlan createSearchPlan(RecommendationContext context) {
            lastContext = context;
            return new PlaceSearchPlan(List.of(new PlaceSearchPlan.Query(
                    List.of("울산 문화시설"), "KEYWORD", List.of("14"),
                    context.regionCode(), context.sigunguCode(), null, null, 15_000, 1)), 1);
        }

        @Override
        public PlaceSearchPlan refineSearchPlan(RecommendationContext context,
                                                PlaceSearchPlan previousPlan,
                                                List<TourPlaceCandidate> candidates) {
            return previousPlan;
        }

        @Override
        public CandidateDecision decide(RecommendationContext context,
                                        List<TourPlaceCandidate> candidates) {
            return new CandidateDecision(selections, "상황을 반영했습니다.");
        }
    }

    private static final class CapturingSnapshotWriter implements PlaceSnapshotWriter {
        private List<TourPlaceCandidate> saved = List.of();

        @Override
        public Map<String, Long> save(List<TourPlaceCandidate> candidates, String destination) {
            saved = List.copyOf(candidates);
            return Map.of("first", 101L, "second", 102L, "third", 103L);
        }
    }
}
