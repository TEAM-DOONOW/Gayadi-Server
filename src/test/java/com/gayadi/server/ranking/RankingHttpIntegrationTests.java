package com.gayadi.server.ranking;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/** Android 홈 순위 호출 계약(인증, 입력 검증, 응답 형태)을 실제 HTTP로 검증합니다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingHttpIntegrationTests {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void requiresAuthentication() throws Exception {
        Assertions.assertThat(get("/api/v1/rankings?type=RESTAURANT", null).statusCode()).isEqualTo(401);
    }

    @Test
    void validatesTypeLimitAndRegion() throws Exception {
        String token = register();
        assertError(get("/api/v1/rankings?type=BOGUS", token), 400);
        assertError(get("/api/v1/rankings", token), 400);
        assertError(get("/api/v1/rankings?type=FESTIVAL&limit=0", token), 400);
        assertError(get("/api/v1/rankings?type=FESTIVAL&limit=21", token), 400);
        assertError(get("/api/v1/rankings?type=FESTIVAL&region=" + encode("x".repeat(51)), token), 400);
        JsonNode unsupported = assertError(get("/api/v1/rankings?type=ATTRACTION&region=" + encode("아틀란티스"), token), 400);
        Assertions.assertThat(unsupported.path("code").asString()).isEqualTo("TOUR_REGION_UNSUPPORTED");
    }

    @Test
    void returnsAndroidContractForEveryTypeWithoutExternalKeys() throws Exception {
        String token = register();
        for (String type : new String[] {"ATTRACTION", "FESTIVAL", "REGION", "RESTAURANT"}) {
            HttpResponse<String> response = get("/api/v1/rankings?type=" + type + "&limit=10", token);
            Assertions.assertThat(response.statusCode())
                    .withFailMessage("%s HTTP %s: %s", type, response.statusCode(), response.body())
                    .isEqualTo(200);
            JsonNode body = json.readTree(response.body());
            Assertions.assertThat(body.path("type").asString()).isEqualTo(type);
            Assertions.assertThat(body.has("providerDataAvailable")).isTrue();
            Assertions.assertThat(body.path("items").isArray()).isTrue();
        }
        // 테스트 환경은 외부 API 키가 없으므로 관광지는 서울 기준 대체 목록으로 응답합니다.
        JsonNode attraction = json.readTree(get("/api/v1/rankings?type=ATTRACTION", token).body());
        Assertions.assertThat(attraction.path("region").asString()).isEqualTo("서울");
        Assertions.assertThat(attraction.path("providerDataAvailable").asBoolean()).isFalse();
    }

    @Test
    void documentsRankingsWithBearerAuth() throws Exception {
        JsonNode document = json.readTree(get("/api/openapi", null).body());
        JsonNode operation = document.path("paths").path("/api/v1/rankings").path("get");
        Assertions.assertThat(operation.isMissingNode()).isFalse();
        Assertions.assertThat(operation.path("security").toString()).contains("bearerAuth");
        Assertions.assertThat(operation.path("parameters").toString()).contains("type", "region", "limit");
    }

    private String register() throws Exception {
        String email = "ranking-" + System.nanoTime() + "@example.com";
        HttpResponse<String> response = send("POST", "/api/v1/auth/registrations", null,
                "{\"email\":\"" + email + "\",\"password\":\"password1\",\"nickname\":\"순위\"}");
        Assertions.assertThat(response.statusCode()).isEqualTo(201);
        return json.readTree(response.body()).path("accessToken").asString();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode assertError(HttpResponse<String> response, int status) {
        Assertions.assertThat(response.statusCode())
                .withFailMessage("HTTP %s: %s", response.statusCode(), response.body())
                .isEqualTo(status);
        JsonNode body = json.readTree(response.body());
        Assertions.assertThat(body.path("status").asInt()).isEqualTo(status);
        Assertions.assertThat(body.path("traceId").asString()).isNotBlank();
        return body;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
