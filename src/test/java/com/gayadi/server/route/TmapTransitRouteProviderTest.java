package com.gayadi.server.route;

import com.gayadi.server.common.Location;
import com.gayadi.server.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.concurrent.CopyOnWriteArrayList;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TmapTransitRouteProviderTest {

    private HttpServer server;
    private AtomicReference<String> receivedAppKey;
    private AtomicReference<String> receivedBody;
    private String customResponse;
    private final List<String> requestedTimes = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        receivedAppKey = new AtomicReference<>();
        receivedBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/transit/routes", this::respond);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void convertsTmapTransitResponseToRouteEstimate() {
        TmapTransitRouteProvider provider = new TmapTransitRouteProvider(
                new ObjectMapper(), "test-app-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/transit/routes");

        assertThat(provider.providerName()).isEqualTo("TMAP_TRANSIT");

        List<RouteProvider.RouteEstimate> estimates = provider.estimateSegments(List.of(
                new Location("출발", 35.5384, 129.3114),
                new Location("도착", 35.5430, 129.3272)), "IN_TRIP");

        assertThat(estimates).hasSize(1);
        assertThat(estimates.getFirst().durationMinutes()).isEqualTo(21);
        assertThat(estimates.getFirst().transferCount()).isEqualTo(2);
        assertThat(estimates.getFirst().fare()).isEqualTo(1_450);
        assertThat(estimates.getFirst().summary()).contains("BUS 123");
        assertThat(estimates.getFirst().providerName()).isEqualTo("TMAP_TRANSIT");
        assertThat(receivedAppKey.get()).isEqualTo("test-app-key");
        assertThat(receivedBody.get()).contains("startX", "startY", "endX", "endY");
    }

    @Test
    void fallsBackToLocalEstimateWhenTmapAuthenticationFails() {
        server.removeContext("/transit/routes");
        server.createContext("/transit/routes", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });
        TmapTransitRouteProvider provider = new TmapTransitRouteProvider(
                new ObjectMapper(), "invalid-app-key",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/transit/routes", true);

        List<RouteProvider.RouteEstimate> estimates = provider.estimateSegments(List.of(
                new Location("출발", 35.5384, 129.3114),
                new Location("도착", 35.5430, 129.3272)), "IN_TRIP");

        assertThat(estimates).hasSize(1);
        assertThat(estimates.getFirst().providerName()).isEqualTo("LOCAL_ESTIMATE");
        assertThat(estimates.getFirst().summary()).contains("실제 교통 정보와 다를 수 있습니다");
    }

    @Test
    void choosesFastestValidOperatingRouteRatherThanFirstResult() {
        customResponse = """
                {"plan":{"itineraries":[
                  {"totalTime":1800,"transferCount":0},
                  {"totalTime":600,"transferCount":2},
                  {"totalTime":600,"transferCount":1},
                  {"totalTime":300,"transferCount":0,"legs":[{"mode":"BUS","service":0}]},
                  {"totalTime":-1,"transferCount":0}
                ]}}
                """;
        var result = provider(false).estimateSegments(stops(), "IN_TRIP").getFirst();
        assertThat(result.durationMinutes()).isEqualTo(10);
        assertThat(result.transferCount()).isEqualTo(1);
    }

    @Test
    void selectsFewestTransfersThenShortestTime() {
        customResponse = """
                {"plan":{"itineraries":[
                  {"totalTime":600,"transferCount":2},
                  {"totalTime":1800,"transferCount":0},
                  {"totalTime":1200,"transferCount":0}
                ]}}
                """;
        var result = provider(false).estimateSegments(stops(), "IN_TRIP",
                new TransitRoutingOptions(OffsetDateTime.parse("2026-10-01T10:00:00+09:00"),
                        TransitPreference.FEWER_TRANSFERS, 0)).getFirst();
        assertThat(result.durationMinutes()).isEqualTo(20);
        assertThat(result.transferCount()).isZero();
    }

    @Test
    void usesKoreanScheduledTimeAndAdvancesByTravelAndStayAcrossMidnight() {
        var result = provider(false).estimateSegments(List.of(
                new Location("A", 37, 127), new Location("B", 37.1, 127.1),
                new Location("C", 37.2, 127.2)), "IN_TRIP",
                new TransitRoutingOptions(OffsetDateTime.parse("2026-10-01T14:50:00Z"),
                        TransitPreference.FASTEST, 60));
        assertThat(result).hasSize(2);
        assertThat(requestedTimes).containsExactly("202610012350", "202610020111");
    }

    @Test
    void doesNotReplaceClosedTransitWithLocalEstimateEvenWhenFallbackEnabled() {
        customResponse = """
                {"plan":{"itineraries":[{"totalTime":600,"transferCount":0,
                  "legs":[{"mode":"SUBWAY","service":0}]}]}}
                """;
        assertThatThrownBy(() -> provider(true).estimateSegments(stops(), "IN_TRIP"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(RouteErrorCode.TMAP_ROUTE_UNAVAILABLE));
    }

    private TmapTransitRouteProvider provider(boolean fallback) {
        return new TmapTransitRouteProvider(new ObjectMapper(), "placeholder",
                "http://127.0.0.1:" + server.getAddress().getPort() + "/transit/routes", fallback);
    }

    private List<Location> stops() {
        return List.of(new Location("A", 37, 127), new Location("B", 37.1, 127.1));
    }

    private void respond(HttpExchange exchange) throws IOException {
        receivedAppKey.set(exchange.getRequestHeaders().getFirst("appKey"));
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        requestedTimes.add(new ObjectMapper().readTree(receivedBody.get()).path("searchDttm").asString());
        String response = customResponse != null ? customResponse : """
                {
                  "plan": {
                    "itineraries": [{
                      "totalTime": 1250,
                      "transferCount": 2,
                      "fare": {"regular": {"totalFare": 1450}},
                      "legs": [
                        {"mode": "WALK", "service": 1},
                        {"mode": "BUS", "route": "123", "service": 1}
                      ]
                    }]
                  }
                }
                """;
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
