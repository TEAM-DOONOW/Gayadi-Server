package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HttpSmokeIntegrationTests {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void servesHealthSurveyAndPlacesOverHttp() throws Exception {
        HttpResponse<String> health = get("/actuator/health");
        HttpResponse<String> survey = get("/api/v1/surveys/personality");
        HttpResponse<String> places = get("/api/v1/places");

        Assertions.assertThat(health.statusCode()).isEqualTo(200);
        Assertions.assertThat(health.body()).contains("\"status\":\"UP\"");
        Assertions.assertThat(survey.statusCode()).isEqualTo(200);
        Assertions.assertThat(survey.body()).contains("여행 성향 검사");
        Assertions.assertThat(places.statusCode()).isEqualTo(200);
        Assertions.assertThat(places.body()).contains("서울숲", "국립중앙박물관");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
