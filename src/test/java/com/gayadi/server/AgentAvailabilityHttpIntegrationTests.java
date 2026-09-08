package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentAvailabilityHttpIntegrationTests {

    @LocalServerPort
    int port;

    @org.springframework.beans.factory.annotation.Autowired
    ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void disabledAgentReturns503AndOwnerCanSetDeparturePlaces() throws Exception {
        JsonNode signup = body(request("POST", "/api/v1/auth/registrations", null, """
                {"email":"agent-off-%s@example.com","password":"password1","nickname":"에이전트"}
                """.formatted(System.nanoTime())), 201);
        String token = signup.path("accessToken").asString();

        JsonNode unavailable = body(request("POST", "/api/v1/recommendations/places", token, """
                {
                  "destination":"서울",
                  "profile":"여유 있는 여행을 좋아합니다.",
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "externalProcessingConsent":true
                }
                """), 503);
        Assertions.assertThat(unavailable.path("code").asString())
                .isEqualTo("RECOMMENDATION_UNAVAILABLE");

        LocalDate day = LocalDate.now().plusDays(2);
        JsonNode trip = body(request("POST", "/api/v1/trips", token, """
                {
                  "name":"출발지 확인",
                  "startDate":"%s",
                  "endDate":"%s",
                  "cities":["서울"],
                  "departurePlaceId":1,
                  "returnPlaceId":1
                }
                """.formatted(day, day)), 201);
        long tripId = trip.path("id").asLong();

        JsonNode situation = body(request(
                "POST", "/api/v1/trips/" + tripId + "/situation-responses", token, """
                {
                  "latitude":37.5665,
                  "longitude":126.9780,
                  "externalProcessingConsent":true,
                  "situation":{}
                }
                """), 503);
        Assertions.assertThat(situation.path("code").asString())
                .isEqualTo("SITUATION_AGENT_UNAVAILABLE");

        JsonNode members = body(request(
                "GET", "/api/v1/trips/" + tripId + "/participants", token, null), 200);
        Assertions.assertThat(members.get(0).path("departurePlaceId").asLong()).isEqualTo(1L);
        Assertions.assertThat(members.get(0).path("returnPlaceId").asLong()).isEqualTo(1L);

        JsonNode updated = body(request(
                "PATCH", "/api/v1/trips/" + tripId + "/participants/current", token, """
                {"departurePlaceId":2,"returnPlaceId":3}
                """), 200);
        Assertions.assertThat(updated.path("departurePlaceId").asLong()).isEqualTo(2L);
        Assertions.assertThat(updated.path("returnPlaceId").asLong()).isEqualTo(3L);
    }

    private JsonNode body(HttpResponse<String> response, int expectedStatus) {
        Assertions.assertThat(response.statusCode())
                .withFailMessage("HTTP %s: %s", response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        if (response.body() == null || response.body().isBlank()) {
            return json.createObjectNode();
        }
        return json.readTree(response.body());
    }

    private HttpResponse<String> request(String method, String path, String token, String body)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + path));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
