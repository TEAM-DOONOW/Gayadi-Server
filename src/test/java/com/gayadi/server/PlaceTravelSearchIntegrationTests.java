package com.gayadi.server;

import com.gayadi.server.auth.JwtService;
import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.Location;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.route.KakaoDirectionsRouteProvider;
import com.gayadi.server.route.RouteErrorCode;
import com.gayadi.server.route.RouteProvider;
import com.gayadi.server.route.TransportMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PlaceTravelSearchIntegrationTests {
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired UserService users;
    @Autowired JwtService jwt;
    @MockitoBean KakaoDirectionsRouteProvider car;
    private final HttpClient client = HttpClient.newHttpClient();
    private String prefix;
    private String accessToken;

    @BeforeEach
    void prepare() {
        prefix = "travelsearch" + Long.toUnsignedString(System.nanoTime());
        accessToken = jwt.issue(users.create(prefix).id());
        when(car.transportMode()).thenReturn(TransportMode.CAR);
        when(car.providerName()).thenReturn("KAKAO_DIRECTIONS");
        // 가까운 A는 도로로 20분, 먼 B는 5분입니다. 다음 방문지가 있으면 A의 경유 비용이 작습니다.
        when(car.estimateSegments(anyList(), anyString())).thenAnswer(invocation -> {
            List<Location> stops = invocation.getArgument(0);
            List<RouteProvider.RouteEstimate> result = new ArrayList<>();
            for (int i = 1; i < stops.size(); i++) {
                int minutes = stops.get(i).label().endsWith("-A") ? 20
                        : stops.get(i).label().endsWith("-B") ? 5
                        : stops.get(i - 1).label().endsWith("-A") ? 1
                        : stops.get(i - 1).label().endsWith("-B") ? 30 : 10;
                result.add(new RouteProvider.RouteEstimate(minutes, 0, 0, "mock", "KAKAO_DIRECTIONS"));
            }
            return result;
        });
    }

    @Test
    void ranksByRoadTimePreservingFiltersAndCoordinates() throws Exception {
        add("A", 37.001, "PUBLIC", "CAFE", "ACTIVE");
        add("B", 37.01, "PUBLIC", "CAFE", "ACTIVE");
        add("private", 37.002, "PRIVATE", "CAFE", "ACTIVE");
        add("inactive", 37.003, "PUBLIC", "CAFE", "INACTIVE");
        add("restaurant", 37.004, "PUBLIC", "RESTAURANT", "ACTIVE");
        JsonNode result = body(get(travelQuery() + "&category=CAFE&region=1", true), 200);
        assertThat(names(result)).containsExactly(prefix + "-B", prefix + "-A");
        assertThat(result.path("items").get(0).path("latitude").asDouble()).isEqualTo(37.01);
        JsonNode time = result.path("items").get(0).path("travelTime");
        assertThat(time.path("durationMinutes").asInt()).isEqualTo(5);
        assertThat(time.path("transportMode").asString()).isEqualTo("CAR");
        assertThat(time.path("fallback").asBoolean()).isFalse();
        assertThat(result.path("ranking").path("sort").asString()).isEqualTo("TRAVEL_TIME");
        assertThat(result.path("hasNext").asBoolean()).isFalse();
        assertThat(result.path("nextCursor").isNull()).isTrue();
    }

    @Test
    void usesAdditionalTravelTimeWhenInsertingBetweenVisits() throws Exception {
        add("A", 37.001, "PUBLIC", "CAFE", "ACTIVE");
        add("B", 37.01, "PUBLIC", "CAFE", "ACTIVE");
        JsonNode result = body(get(travelQuery() + "&nextLatitude=37.02&nextLongitude=127", true), 200);
        assertThat(names(result)).containsExactly(prefix + "-A", prefix + "-B");
        JsonNode first = result.path("items").get(0).path("travelTime");
        assertThat(first.path("durationMinutes").asInt()).isEqualTo(20);
        assertThat(first.path("onwardDurationMinutes").asInt()).isEqualTo(1);
        assertThat(first.path("additionalDurationMinutes").asLong()).isEqualTo(11);
    }

    @Test
    void noOriginKeepsPublicSearchAndExistingCursorPagination() throws Exception {
        add("A", 37.001, "PUBLIC", "CAFE", "ACTIVE");
        add("B", 37.01, "PUBLIC", "CAFE", "ACTIVE");
        JsonNode first = body(get("?query=" + prefix + "&sort=TRAVEL_TIME&limit=1", false), 200);
        assertThat(names(first)).containsExactly(prefix + "-B");
        assertThat(first.path("ranking").path("sort").asString()).isEqualTo("RECENT");
        assertThat(first.path("hasNext").asBoolean()).isTrue();
        JsonNode second = body(get("?query=" + prefix + "&limit=1&cursor="
                + first.path("nextCursor").asLong(), false), 200);
        assertThat(names(second)).containsExactly(prefix + "-A");
        verify(car, never()).estimateSegments(anyList(), anyString());
    }

    @Test
    void transitSelectionUsesExistingTransitProvider() throws Exception {
        add("A", 37.001, "PUBLIC", "CAFE", "ACTIVE");
        JsonNode result = body(get(travelQuery().replace("CAR", "PUBLIC_TRANSIT"), true), 200);
        assertThat(result.path("items").get(0).path("travelTime").path("configuredProvider").asString())
                .isEqualTo("LOCAL_ESTIMATE");
        verify(car, never()).estimateSegments(anyList(), anyString());
    }

    @Test
    void validatesCoordinatesModeCursorAndAuthenticationBeforeProviderCalls() throws Exception {
        assertThat(get(travelQuery(), false).statusCode()).isEqualTo(401);
        assertThat(get(travelQuery() + "&cursor=1", true).statusCode()).isEqualTo(400);
        assertThat(get(travelQuery().replace("originLatitude=37", "originLatitude=91"), true).statusCode())
                .isEqualTo(400);
        assertThat(get("?sort=TRAVEL_TIME&originLatitude=37", true).statusCode()).isEqualTo(400);
        assertThat(get("?sort=TRAVEL_TIME&nextLatitude=37&nextLongitude=127", true).statusCode()).isEqualTo(400);
        assertThat(get(travelQuery().replace("CAR", "FLIGHT"), true).statusCode()).isEqualTo(400);
        verify(car, never()).estimateSegments(anyList(), anyString());
    }

    @Test
    void boundsCandidatePoolBeforeRoadQueriesAndMarksLimitedResults() throws Exception {
        for (int i = 0; i < 22; i++) add("candidate" + i, 37.001 + i * 0.001, "PUBLIC", "CAFE", "ACTIVE");
        JsonNode result = body(get(travelQuery() + "&limit=50", true), 200);
        assertThat(result.path("items").size()).isEqualTo(20);
        assertThat(result.path("ranking").path("limited").asBoolean()).isTrue();
        assertThat(result.path("ranking").path("evaluatedCandidates").asInt()).isEqualTo(20);
        assertThat(names(result)).doesNotContain(prefix + "-candidate20", prefix + "-candidate21");
    }

    @Test
    void providerFailureDoesNotReturnApparentlyCompleteRankings() throws Exception {
        add("A", 37.001, "PUBLIC", "CAFE", "ACTIVE");
        when(car.estimateSegments(anyList(), anyString()))
                .thenThrow(new BusinessException(RouteErrorCode.KAKAO_RATE_LIMITED));
        body(get(travelQuery(), true), 429);
    }

    @Test
    void excludesUnreachableCandidate() throws Exception {
        add("A", 37.001, "PUBLIC", "CAFE", "ACTIVE");
        when(car.estimateSegments(anyList(), anyString()))
                .thenThrow(new BusinessException(RouteErrorCode.KAKAO_ROUTE_UNAVAILABLE));
        assertThat(body(get(travelQuery(), true), 200).path("items").isEmpty()).isTrue();
    }

    @Test
    void exposesTravelSearchAsQueryParametersInOpenApi() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/openapi")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        JsonNode operation = body(response, 200).path("paths").path("/api/v1/places").path("get");
        List<String> queryNames = new ArrayList<>();
        operation.path("parameters").forEach(parameter -> {
            assertThat(parameter.path("in").asString()).isEqualTo("query");
            queryNames.add(parameter.path("name").asString());
        });
        assertThat(queryNames).contains("query", "region", "category", "cursor", "limit", "sort",
                "transportMode", "originLatitude", "originLongitude", "nextLatitude", "nextLongitude");
    }

    @Test
    void marksFallbackAndExcludesExistingVisitCoordinates() throws Exception {
        add("origin", 37.0, "PUBLIC", "CAFE", "ACTIVE");
        add("A", 37.001, "PUBLIC", "CAFE", "ACTIVE");
        when(car.estimateSegments(anyList(), anyString())).thenReturn(List.of(
                new RouteProvider.RouteEstimate(12, 0, 0, "추정", "LOCAL_ESTIMATE")));
        JsonNode result = body(get(travelQuery(), true), 200);
        assertThat(names(result)).containsExactly(prefix + "-A");
        assertThat(result.path("items").get(0).path("travelTime").path("fallback").asBoolean()).isTrue();
    }

    private String travelQuery() {
        return "?query=" + prefix + "&sort=TRAVEL_TIME&transportMode=CAR&originLatitude=37&originLongitude=127";
    }

    private void add(String suffix, double latitude, String visibility, String category, String status) {
        jdbc.sql("""
                INSERT INTO places (source, name, visibility, category, latitude, longitude, region_id, status)
                VALUES ('USER', ?, ?, ?, ?, 127, 1, ?)
                """).params(prefix + "-" + suffix, visibility, category, latitude, status).update();
    }

    private HttpResponse<String> get(String query, boolean authenticated) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/places" + query));
        if (authenticated) request.header("Authorization", "Bearer " + accessToken);
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode body(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        return mapper.readTree(response.body());
    }

    private List<String> names(JsonNode response) {
        List<String> result = new ArrayList<>();
        response.path("items").forEach(item -> result.add(item.path("name").asString()));
        return result;
    }
}
