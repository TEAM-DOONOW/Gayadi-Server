package com.gayadi.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WeatherApiHttpIntegrationTests {

    private static HttpServer weatherServer;
    private static final AtomicInteger forecastRequests = new AtomicInteger();

    @LocalServerPort
    int port;

    @org.springframework.beans.factory.annotation.Autowired
    ObjectMapper json;

    private final HttpClient client = HttpClient.newHttpClient();

    @DynamicPropertySource
    static void weatherProperties(DynamicPropertyRegistry registry) {
        ensureWeatherServer();
        registry.add("weather.api.key", () -> "test-weather-key");
        registry.add("weather.api.base-url", () -> "http://127.0.0.1:"
                + weatherServer.getAddress().getPort());
    }

    @AfterAll
    static void stopWeatherServer() {
        if (weatherServer != null) weatherServer.stop(0);
    }

    @Test
    void weatherRequiresAuthenticationAndReturnsValidatedObservation() throws Exception {
        HttpResponse<String> unauthorized = get(
                "/api/v1/weather/now?nx=60&ny=127&baseDate=20260825&baseTime=1400", null);
        Assertions.assertThat(unauthorized.statusCode()).isEqualTo(401);

        String token = register();
        JsonNode now = body(get(
                "/api/v1/weather/now?lat=37.563569&lon=126.980008"
                        + "&baseDate=20260825&baseTime=1400", token), 200);
        Assertions.assertThat(now.path("nx").asInt()).isEqualTo(60);
        Assertions.assertThat(now.path("ny").asInt()).isEqualTo(127);
        Assertions.assertThat(now.path("temperature").asString()).isEqualTo("27.1");
        Assertions.assertThat(now.path("precipitationTypeName").asString()).isEqualTo("없음");

        Assertions.assertThat(get(
                "/api/v1/weather/now?nx=0&ny=127&baseDate=20260825&baseTime=1400", token)
                .statusCode()).isEqualTo(400);
        Assertions.assertThat(get(
                "/api/v1/weather/now?lat=37.56&lon=126.98&nx=60&ny=127"
                        + "&baseDate=20260825&baseTime=1400", token)
                .statusCode()).isEqualTo(400);

        JsonNode forecast = body(get(
                "/api/v1/weather/forecast?nx=60&ny=127"
                        + "&baseDate=20260825&baseTime=1400", token), 200);
        Assertions.assertThat(forecast.path("forecast").size()).isEqualTo(2);
        Assertions.assertThat(forecast.path("forecast").get(0).path("skyName").asString())
                .isEqualTo("구름많음");
        Assertions.assertThat(forecastRequests).hasValue(2);
    }

    @Test
    void openApiDocumentsWeatherSecurityConstraintsAndResponses() throws Exception {
        JsonNode paths = body(get("/api/openapi", null), 200).path("paths");
        JsonNode operation = paths.path("/api/v1/weather/forecast").path("get");

        Assertions.assertThat(operation.path("security").toString()).contains("bearerAuth");
        Assertions.assertThat(operation.path("responses").has("200")).isTrue();
        Assertions.assertThat(operation.path("responses").has("401")).isTrue();
        Assertions.assertThat(operation.path("responses").has("502")).isTrue();
        JsonNode nx = findParameter(operation.path("parameters"), "nx");
        Assertions.assertThat(nx.path("schema").path("minimum").asInt()).isEqualTo(1);
        Assertions.assertThat(nx.path("schema").path("maximum").asInt()).isEqualTo(149);
        for (String path : new String[]{
                "/api/v1/weather/now",
                "/api/v1/weather/ultra-forecast",
                "/api/v1/weather/forecast"}) {
            JsonNode parameters = paths.path(path).path("get").path("parameters");
            Assertions.assertThat(parameters).hasSize(6);
            Assertions.assertThat(findParameter(parameters, "lat").path("schema")
                    .path("minimum").asDouble()).isEqualTo(-90.0);
            Assertions.assertThat(findParameter(parameters, "ny").path("schema")
                    .path("maximum").asInt()).isEqualTo(253);
        }
        Assertions.assertThat(paths.path("/api/v1/trips/{tripId}/situation-responses")
                .path("post").path("description").asString())
                .contains("기상청 초단기실황");
    }

    private JsonNode findParameter(JsonNode parameters, String name) {
        for (JsonNode parameter : parameters) {
            if (name.equals(parameter.path("name").asString())) return parameter;
        }
        throw new AssertionError("OpenAPI parameter not found: " + name);
    }

    private String register() throws Exception {
        String email = "weather-" + System.nanoTime() + "@example.com";
        HttpRequest request = HttpRequest.newBuilder(uri("/api/v1/auth/registrations"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"email\":\"" + email
                                + "\",\"password\":\"password1\",\"nickname\":\"날씨사용자\"}"))
                .build();
        return body(client.send(request, HttpResponse.BodyHandlers.ofString()), 201)
                .path("accessToken").asString();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path)).GET();
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private JsonNode body(HttpResponse<String> response, int expectedStatus) {
        Assertions.assertThat(response.statusCode())
                .withFailMessage("HTTP %s: %s", response.statusCode(), response.body())
                .isEqualTo(expectedStatus);
        return json.readTree(response.body());
    }

    private static synchronized void ensureWeatherServer() {
        if (weatherServer != null) return;
        try {
            weatherServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            weatherServer.createContext("/getUltraSrtNcst", WeatherApiHttpIntegrationTests::weather);
            weatherServer.createContext("/getVilageFcst", WeatherApiHttpIntegrationTests::forecast);
            weatherServer.start();
        } catch (IOException exception) {
            throw new IllegalStateException("기상청 테스트 서버를 시작하지 못했습니다.", exception);
        }
    }

    private static void weather(HttpExchange exchange) throws IOException {
        String response = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                  "body":{"pageNo":1,"numOfRows":1000,"totalCount":8,"items":{"item":[
                    {"category":"T1H","obsrValue":"27.1"},
                    {"category":"RN1","obsrValue":"0"},
                    {"category":"UUU","obsrValue":"1.0"},
                    {"category":"VVV","obsrValue":"0.5"},
                    {"category":"REH","obsrValue":"72"},
                    {"category":"PTY","obsrValue":"0"},
                    {"category":"VEC","obsrValue":"180"},
                    {"category":"WSD","obsrValue":"2.1"}
                  ]}}}}
                """;
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void forecast(HttpExchange exchange) throws IOException {
        forecastRequests.incrementAndGet();
        boolean secondPage = exchange.getRequestURI().getRawQuery().contains("pageNo=2");
        String items = secondPage
                ? """
                    {"fcstDate":"20260826","fcstTime":"1500","category":"PTY","fcstValue":"0"},
                    {"fcstDate":"20260826","fcstTime":"1600","category":"TMP","fcstValue":"24"}
                    """
                : """
                    {"fcstDate":"20260826","fcstTime":"1500","category":"TMP","fcstValue":"25"},
                    {"fcstDate":"20260826","fcstTime":"1500","category":"SKY","fcstValue":"3"}
                    """;
        int page = secondPage ? 2 : 1;
        String response = """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                  "body":{"pageNo":%d,"numOfRows":2,"totalCount":4,
                  "items":{"item":[%s]}}}}
                """.formatted(page, items);
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
