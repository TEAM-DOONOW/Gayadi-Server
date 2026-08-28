package com.gayadi.server;

import com.gayadi.server.recommendation.PlaceRecommendationAgent;
import com.gayadi.server.recommendation.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.PlaceRecommendationResponse;
import com.gayadi.server.recommendation.PlaceSearchPlan;
import com.gayadi.server.recommendation.PlaceSnapshotWriter;
import com.gayadi.server.recommendation.RecommendationLanguageModel;
import com.gayadi.server.recommendation.RecommendedPlace;
import com.gayadi.server.recommendation.SituationResponse;
import com.gayadi.server.recommendation.SituationResponseAgent;
import com.gayadi.server.recommendation.TourPlaceCandidate;
import com.gayadi.server.recommendation.TourPlaceSearchGateway;
import com.gayadi.server.recommendation.TravelSituation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.ai.enabled=true",
        "app.ai.embedding.enabled=false",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.chat.model=gpt-4o-mini"
})
@Import(ApiFirstAgentSystemTests.FakeAgentConfiguration.class)
class ApiFirstAgentSystemTests {

    @org.springframework.beans.factory.annotation.Autowired
    PlaceRecommendationAgent recommendationAgent;

    @org.springframework.beans.factory.annotation.Autowired
    SituationResponseAgent situationAgent;

    @Test
    void bootsTheApiFirstAgentWithoutAnEmbeddingModel() {
        PlaceRecommendationResponse response = recommendationAgent.recommendPlaces(request(
                new TravelSituation(
                        new TravelSituation.Weather("RAIN", "비", 10.0, 80, 23.0, 2.0),
                        TravelSituation.Congestion.empty(),
                        TravelSituation.Transit.empty())));

        assertThat(response.recommendations()).extracting(RecommendedPlace::placeId)
                .containsExactly("indoor-1");
    }

    @Test
    void situationResponseMarksMissedTransitAsRequiringRouteRecalculation() {
        SituationResponse response = situationAgent.respond(request(new TravelSituation(
                TravelSituation.Weather.empty(),
                TravelSituation.Congestion.empty(),
                new TravelSituation.Transit(true, "BUS", 20, "버스를 놓침", ""))));

        assertThat(response.routeRecalculationRequired()).isTrue();
        assertThat(response.nextAction()).contains("대체 경로");
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAgentConfiguration {

        @Bean
        @Primary
        RecommendationLanguageModel recommendationLanguageModel() {
            return new RecommendationLanguageModel() {
                @Override
                public PlaceSearchPlan createSearchPlan(RecommendationContext context) {
                    return new PlaceSearchPlan(List.of(new PlaceSearchPlan.Query(
                            List.of("울산 실내 문화시설"), "KEYWORD", List.of("14"),
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
                    return new CandidateDecision(List.of(new CandidateDecision.Selection(
                            "indoor-1", "비를 피할 수 있는 문화시설입니다.", 0.95)),
                            "날씨를 반영했습니다.");
                }
            };
        }

        @Bean
        @Primary
        TourPlaceSearchGateway tourPlaceSearchGateway() {
            return (plan, context) -> List.of(
                    new TourPlaceCandidate("outdoor-1", "울산 공원", "ATTRACTION", "12",
                            "울산", 35.54, 129.31, false, 1.0, "야외 공원", ""),
                    new TourPlaceCandidate("indoor-1", "울산 문화시설", "CULTURE", "14",
                            "울산", 35.54, 129.31, true, 2.0, "실내 전시 공간", ""));
        }

        @Bean
        @Primary
        PlaceSnapshotWriter placeSnapshotWriter() {
            return (candidates, destination) -> java.util.Map.of();
        }
    }
}
