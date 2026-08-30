package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiErrorResponseIntegrationTests {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void returnsCommonErrorContractForUnknownPublicPath() throws Exception {
        HttpResponse<String> response = get("/api/openapi/not-found");

        Assertions.assertThat(response.statusCode()).isEqualTo(404);
        JsonNode body = objectMapper.readTree(response.body());
        assertCommonFields(body, 404, "RESOURCE_NOT_FOUND", "/api/openapi/not-found");
        Assertions.assertThat(body.path("details").isNull()).isTrue();
    }

    @Test
    void returnsMethodNotAllowedWithAllowHeader() throws Exception {
        HttpResponse<String> response = get("/api/v1/auth/tokens");

        Assertions.assertThat(response.statusCode()).isEqualTo(405);
        Assertions.assertThat(response.headers().firstValue("Allow")).hasValueSatisfying(
                value -> Assertions.assertThat(value).contains("POST"));
        JsonNode body = objectMapper.readTree(response.body());
        assertCommonFields(body, 405, "METHOD_NOT_ALLOWED", "/api/v1/auth/tokens");
        Assertions.assertThat(body.path("details").isArray()).isTrue();
    }

    @Test
    void returnsCommonErrorContractForValidationFailureWithoutRejectedValues() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/registrations", """
                {
                  "email": "private@example.com",
                  "password": "",
                  "nickname": ""
                }
                """);

        Assertions.assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(response.body());
        assertCommonFields(body, 400, "INVALID_REQUEST", "/api/v1/auth/registrations");
        Assertions.assertThat(body.path("message").asString()).isEqualTo("요청값이 올바르지 않습니다.");
        Assertions.assertThat(body.path("details").isArray()).isTrue();
        Assertions.assertThat(body.path("details").size()).isGreaterThanOrEqualTo(2);
        body.path("details").forEach(detail ->
                Assertions.assertThat(detail.properties().stream().map(java.util.Map.Entry::getKey))
                        .containsExactlyInAnyOrder("field", "message"));
        Assertions.assertThat(response.body())
                .doesNotContain("private@example.com")
                .doesNotContain("rejectedValue");
    }

    @Test
    void returnsNullDetailsForMalformedJson() throws Exception {
        HttpResponse<String> response = post("/api/v1/auth/registrations", "{not-json");

        Assertions.assertThat(response.statusCode()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(response.body());
        assertCommonFields(body, 400, "MALFORMED_REQUEST_BODY", "/api/v1/auth/registrations");
        Assertions.assertThat(body.has("details")).isTrue();
        Assertions.assertThat(body.path("details").isNull()).isTrue();
    }

    private void assertCommonFields(JsonNode body, int status, String code, String path) {
        Assertions.assertThat(body.properties().stream().map(java.util.Map.Entry::getKey))
                .containsExactlyInAnyOrderElementsOf(Set.of(
                        "timestamp", "status", "code", "message", "path", "traceId", "details"));
        Assertions.assertThat(body.path("timestamp").asString()).isNotBlank();
        Assertions.assertThat(body.path("status").asInt()).isEqualTo(status);
        Assertions.assertThat(body.path("code").asString()).isEqualTo(code);
        Assertions.assertThat(body.path("message").asString()).isNotBlank();
        Assertions.assertThat(body.path("path").asString()).isEqualTo(path);
        Assertions.assertThat(body.path("traceId").asString()).isNotBlank();
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
