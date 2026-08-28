package com.gayadi.server;

import com.gayadi.server.recommendation.PlaceSearchPlan;
import com.gayadi.server.recommendation.RecommendationLanguageModel;
import com.gayadi.server.recommendation.TourPlaceCandidate;
import com.gayadi.server.recommendation.TourPlaceSearchGateway;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.ai.enabled=true",
        "app.ai.embedding.enabled=false",
        "spring.ai.model.chat=openai",
        "spring.ai.openai.api-key=test-key",
        "spring.ai.openai.chat.model=gpt-4o-mini"
})
@Import(AiUserJourneyIntegrationTests.FakeAgentConfiguration.class)
class AiUserJourneyIntegrationTests {

    @LocalServerPort
    int port;

    @org.springframework.beans.factory.annotation.Autowired
    ObjectMapper json;

    @org.springframework.beans.factory.annotation.Autowired
    JdbcClient jdbc;

    @org.springframework.beans.factory.annotation.Autowired
    TestRecommendationLanguageModel languageModel;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void authenticatedUserCanRecommendReactApproveAndRecalculateRoutes() throws Exception {
        String unique = String.valueOf(System.nanoTime());
        String token = register("ai-user-" + unique + "@example.com");
        LocalDate tripDate = LocalDate.now().plusDays(1);

        JsonNode trip = body(request("POST", "/api/v1/trips", token, """
                {"name":"AI 사용자 여정","startDate":"%s","endDate":"%s","cities":["서울"]}
                """.formatted(tripDate, tripDate)), 201);
        long tripId = trip.path("id").asLong();

        body(request("POST", "/api/v1/trips/" + tripId + "/survey-responses", token,
                surveyAnswers()), 201);
        JsonNode plan = body(request(
                "POST", "/api/v1/trips/" + tripId + "/plans", token, null), 201);
        Assertions.assertThat(plan.path("items").size()).isGreaterThanOrEqualTo(2);

        JsonNode route = body(request(
                "POST", "/api/v1/trips/" + tripId + "/route-recommendations", token,
                "{\"type\":\"ITINERARY\"}"), 201);
        Assertions.assertThat(route.path("options").size()).isEqualTo(2);
        Assertions.assertThat(route.path("segments").size())
                .isEqualTo(route.path("stops").size() - 1);
        Assertions.assertThat(route.path("provider").asText()).isEqualTo("LOCAL_ESTIMATE");
        Assertions.assertThat(route.path("segments").get(0).path("summary").asText())
                .contains("직선거리");
        JsonNode selectedRoute = body(request(
                "PUT", "/api/v1/trips/" + tripId + "/route-selections/ITINERARY", token,
                "{\"optionId\":\"balanced\"}"), 200);
        Assertions.assertThat(selectedRoute.path("provider").asText()).isEqualTo("LOCAL_ESTIMATE");

        JsonNode rain = body(request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "keywords":["박물관","실내"],
                  "limit":3,
                  "targetAt":"%sT14:00:00+09:00",
                  "externalProcessingConsent":true,
                  "situation":{
                    "weather":{
                      "condition":"RAIN",
                      "precipitationType":"비",
                      "precipitationMm":20.0,
                      "precipitationProbability":90,
                      "temperatureC":24.0,
                      "windSpeedMps":3.0
                    }
                  }
                }
                """.formatted(tripDate)), 200);
        Assertions.assertThat(rain.path("situationSummary").asText()).contains("실내");
        Assertions.assertThat(rain.path("routeRecalculationRequired").asBoolean()).isFalse();
        Assertions.assertThat(rain.path("changeProposal").isEmpty()).isTrue();
        Assertions.assertThat(rain.path("placeRecommendations").path("recommendations").size())
                .isEqualTo(1);
        Assertions.assertThat(rain.path("placeRecommendations").path("recommendations")
                .get(0).path("sourcePlaceId").asText()).isEqualTo("indoor-ai");
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM places
                WHERE source = 'TOUR_API' AND source_place_id = 'indoor-ai'
                """).query(Long.class).single()).isEqualTo(1L);
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM places
                WHERE source = 'TOUR_API' AND source_place_id = 'outdoor-ai'
                """).query(Long.class).single()).isZero();

        RecommendationLanguageModel.RecommendationContext rainContext =
                languageModel.lastContext.get();
        Assertions.assertThat(rainContext.destination()).isEqualTo("서울");
        Assertions.assertThat(rainContext.groupSize()).isEqualTo(1);
        Assertions.assertThat(rainContext.purpose()).isEqualTo("SITUATION_RESPONSE");
        Assertions.assertThat(rainContext.policy().indoorRequired()).isTrue();

        JsonNode congestion = body(request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "keywords":["한적한 장소"],
                  "limit":2,
                  "externalProcessingConsent":true,
                  "situation":{
                    "congestion":{"level":"HIGH","occupancyPercent":90,"area":"서울 도심"}
                  }
                }
                """), 200);
        Assertions.assertThat(congestion.path("situationSummary").asText()).contains("혼잡 장소 회피");
        Assertions.assertThat(congestion.path("routeRecalculationRequired").asBoolean()).isFalse();
        Assertions.assertThat(body(request(
                "GET", "/api/v1/trips/" + tripId + "/route-selections", token, null), 200).size())
                .isEqualTo(1);

        JsonNode transit = body(request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "keywords":["가까운 장소"],
                  "limit":2,
                  "externalProcessingConsent":true,
                  "situation":{
                    "transit":{
                      "missed":true,
                      "mode":"BUS",
                      "delayMinutes":20,
                      "reason":"버스를 놓침",
                      "nextDepartureAt":"%sT14:30:00+09:00"
                    }
                  }
                }
                """.formatted(tripDate)), 200);
        Assertions.assertThat(transit.path("routeRecalculationRequired").asBoolean()).isTrue();
        Assertions.assertThat(transit.path("nextAction").asText()).contains("대체 경로");
        Assertions.assertThat(transit.path("placeRecommendations").path("recommendations")
                .get(0).path("sourcePlaceId").asText()).isEqualTo("near-ai");
        Assertions.assertThat(body(request(
                "GET", "/api/v1/trips/" + tripId + "/route-selections", token, null), 200).size())
                .isZero();

        JsonNode refreshedRoute = body(request(
                "POST", "/api/v1/trips/" + tripId + "/route-recommendations", token,
                "{\"type\":\"ITINERARY\"}"), 201);
        body(request("PUT", "/api/v1/trips/" + tripId + "/route-selections/ITINERARY", token,
                "{\"routeId\":" + refreshedRoute.path("id").asLong() + "}"), 200);

        body(request("PATCH", "/api/v1/trips/" + tripId + "/status", token,
                "{\"status\":\"ONGOING\"}"), 200);

        JsonNode activeCongestion = body(request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "keywords":["한적한 야외 장소"],
                  "limit":2,
                  "externalProcessingConsent":true,
                  "situation":{
                    "congestion":{"level":"HIGH","occupancyPercent":92,"area":"서울 도심"}
                  }
                }
                """), 200);
        JsonNode congestionProposal = activeCongestion.path("changeProposal");
        Assertions.assertThat(congestionProposal.path("type").asText())
                .isEqualTo("CONGESTION_CHANGE");
        Assertions.assertThat(congestionProposal.path("options").get(0)
                .path("placeName").asText()).isEqualTo("AI 야외 공원");
        Assertions.assertThat(congestionProposal.path("options").get(0)
                .path("requireIndoor").asBoolean()).isFalse();
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM event_observations
                WHERE source = 'AI_SITUATION_AGENT' AND event_type = 'CONGESTION'
                """).query(Long.class).single()).isEqualTo(1L);

        JsonNode congestionApproved = approve(
                tripId, token, congestionProposal);
        Assertions.assertThat(congestionApproved.path("status").asText()).isEqualTo("APPROVED");
        JsonNode congestionChangedPlan = body(request(
                "GET", "/api/v1/trips/" + tripId + "/plans", token, null), 200);
        Assertions.assertThat(congestionChangedPlan.toString()).contains("AI 야외 공원");
        Assertions.assertThat(body(request(
                "GET", "/api/v1/trips/" + tripId + "/route-selections", token, null), 200).size())
                .isZero();

        JsonNode routeAfterCongestion = body(request(
                "POST", "/api/v1/trips/" + tripId + "/route-recommendations", token,
                "{\"type\":\"ITINERARY\"}"), 201);
        Assertions.assertThat(routeAfterCongestion.path("stops").toString())
                .contains("AI 야외 공원");
        body(request("PUT", "/api/v1/trips/" + tripId + "/route-selections/ITINERARY", token,
                "{\"routeId\":" + routeAfterCongestion.path("id").asLong() + "}"), 200);

        JsonNode firstRainResponse = body(request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "keywords":["박물관","실내"],
                  "limit":3,
                  "externalProcessingConsent":true,
                  "situation":{
                    "weather":{"condition":"RAIN","precipitationProbability":90}
                  }
                }
                """), 200);
        JsonNode firstRainProposal = firstRainResponse.path("changeProposal");
        Assertions.assertThat(firstRainProposal.path("type").asText())
                .isEqualTo("WEATHER_CHANGE");
        Assertions.assertThat(firstRainProposal.path("options").get(0)
                .path("requireIndoor").asBoolean()).isTrue();

        JsonNode latestRainResponse = body(request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "keywords":["박물관","실내"],
                  "limit":3,
                  "externalProcessingConsent":true,
                  "situation":{
                    "weather":{"condition":"RAIN","precipitationProbability":90}
                  }
                }
                """), 200);
        JsonNode latestRainProposal = latestRainResponse.path("changeProposal");
        Assertions.assertThat(jdbc.sql("""
                SELECT status FROM ai_schedule_change_proposals WHERE id = ?
                """).param(firstRainProposal.path("id").asLong()).query(String.class).single())
                .isEqualTo("EXPIRED");
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM ai_schedule_change_proposals
                WHERE trip_id = ? AND status = 'PENDING'
                """).param(tripId).query(Long.class).single()).isEqualTo(1L);

        int revision = latestRainProposal.path("baseRevisionNo").asInt();
        JsonNode approved = approve(tripId, token, latestRainProposal);
        Assertions.assertThat(approved.path("status").asText()).isEqualTo("APPROVED");
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM ai_schedule_change_proposals
                WHERE trip_id = ? AND status = 'PENDING'
                """).param(tripId).query(Long.class).single()).isZero();

        JsonNode changedPlan = body(request(
                "GET", "/api/v1/trips/" + tripId + "/plans", token, null), 200);
        Assertions.assertThat(changedPlan.path("version").asInt()).isEqualTo(revision + 1);
        Assertions.assertThat(changedPlan.toString()).contains("AI 실내 박물관");
        JsonNode staleSelections = body(request(
                "GET", "/api/v1/trips/" + tripId + "/route-selections", token, null), 200);
        Assertions.assertThat(staleSelections.size()).isZero();

        JsonNode recalculated = body(request(
                "POST", "/api/v1/trips/" + tripId + "/route-recommendations", token,
                "{\"type\":\"ITINERARY\"}"), 201);
        Assertions.assertThat(recalculated.path("stops").toString()).contains("AI 실내 박물관");
    }

    private JsonNode approve(long tripId, String token, JsonNode proposal) throws Exception {
        String optionKey = proposal.path("options").get(0).path("key").asText();
        int revision = proposal.path("baseRevisionNo").asInt();
        return body(request(
                "PATCH", "/api/v1/trips/" + tripId + "/change-proposals/"
                        + proposal.path("id").asLong(), token, """
                {"approve":true,"selectedOptionKey":"%s","baseRevisionNo":%d}
                """.formatted(optionKey, revision)), 200);
    }

    @Test
    void situationResponseRequiresAuthenticationAndRejectsInvalidWeatherRange() throws Exception {
        HttpResponse<String> unauthorized = request(
                "POST", "/api/v1/trips/1/situation-responses", null, "{}");
        Assertions.assertThat(unauthorized.statusCode()).isEqualTo(401);

        String token = register("ai-validation-" + System.nanoTime() + "@example.com");
        LocalDate tripDate = LocalDate.now().plusDays(1);
        JsonNode trip = body(request("POST", "/api/v1/trips", token, """
                {"name":"AI 검증 여행","startDate":"%s","endDate":"%s","cities":["서울"]}
                """.formatted(tripDate, tripDate)), 201);
        long tripId = trip.path("id").asLong();
        body(request("POST", "/api/v1/trips/" + tripId + "/survey-responses", token,
                surveyAnswers()), 201);

        HttpResponse<String> invalid = request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "externalProcessingConsent":true,
                  "situation":{"weather":{"precipitationProbability":101}}
                }
                """);
        Assertions.assertThat(invalid.statusCode()).isEqualTo(400);

        HttpResponse<String> invalidTime = request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "regionCode":"11",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "targetAt":"내일 오후",
                  "externalProcessingConsent":true,
                  "situation":{}
                }
                """);
        Assertions.assertThat(invalidTime.statusCode()).isEqualTo(400);
    }

    private String register(String email) throws Exception {
        JsonNode response = body(request(
                "POST", "/api/v1/auth/registrations", null,
                "{\"email\":\"" + email
                        + "\",\"password\":\"password1\",\"nickname\":\"AI사용자\"}"), 201);
        return response.path("accessToken").asText();
    }

    private String surveyAnswers() {
        return """
                {"answers":[
                  {"questionId":"q01","optionId":"a"},
                  {"questionId":"q02","optionId":"a"},
                  {"questionId":"q03","optionId":"a"},
                  {"questionId":"q04","optionId":"a"},
                  {"questionId":"q05","optionId":"a"},
                  {"questionId":"q06","optionId":"a"},
                  {"questionId":"q07","optionId":"a"},
                  {"questionId":"q08","optionId":"a"},
                  {"questionId":"q09","optionId":"a"}
                ]}
                """;
    }

    private JsonNode body(HttpResponse<String> response, int expectedStatus) {
        Assertions.assertThat(response.statusCode())
                .withFailMessage("HTTP %s: %s", response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        return json.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeAgentConfiguration {

        @Bean
        @Primary
        TestRecommendationLanguageModel recommendationLanguageModel() {
            return new TestRecommendationLanguageModel();
        }

        @Bean
        @Primary
        TourPlaceSearchGateway tourPlaceSearchGateway() {
            return (plan, context) -> List.of(
                    new TourPlaceCandidate(
                            "outdoor-ai", "AI 야외 공원", "ATTRACTION", "12", "서울",
                            37.57, 126.99, false, 1.0, "야외 공원", ""),
                    new TourPlaceCandidate(
                            "indoor-ai", "AI 실내 박물관", "CULTURE", "14", "서울",
                            37.56, 126.98, true, 2.0, "실내 전시 시설", ""),
                    new TourPlaceCandidate(
                            "near-ai", "AI 인근 문화공간", "CULTURE", "14", "서울",
                            37.5666, 126.9781, true, 0.1, "현재 위치와 가까운 공간", ""));
        }
    }

    static final class TestRecommendationLanguageModel implements RecommendationLanguageModel {
        private final AtomicReference<RecommendationContext> lastContext = new AtomicReference<>();

        @Override
        public PlaceSearchPlan createSearchPlan(RecommendationContext context) {
            lastContext.set(context);
            return new PlaceSearchPlan(List.of(new PlaceSearchPlan.Query(
                    context.keywords(), "KEYWORD", List.of("12", "14"),
                    context.regionCode(), context.sigunguCode(), null, null, 15_000, 1)), 1);
        }

        @Override
        public PlaceSearchPlan refineSearchPlan(
                RecommendationContext context,
                PlaceSearchPlan previousPlan,
                List<TourPlaceCandidate> candidates) {
            return previousPlan;
        }

        @Override
        public CandidateDecision decide(
                RecommendationContext context,
                List<TourPlaceCandidate> candidates) {
            String selectedId = context.policy().indoorRequired()
                    ? "indoor-ai"
                    : context.policy().transitDisrupted()
                    ? "near-ai"
                    : context.policy().avoidCrowded() ? "outdoor-ai" : candidates.getFirst().placeId();
            return new CandidateDecision(List.of(new CandidateDecision.Selection(
                    selectedId, "실제 여행 상황 변수에 맞는 후보입니다.", 0.95)),
                    context.policy().summary());
        }
    }
}
