package com.gayadi.server.route;

import com.gayadi.server.common.Location;
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

class TmapTransitRouteProviderTest {

    private HttpServer server;
    private AtomicReference<String> receivedAppKey;
    private AtomicReference<String> receivedBody;

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

    private void respond(HttpExchange exchange) throws IOException {
        receivedAppKey.set(exchange.getRequestHeaders().getFirst("appKey"));
        receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String response = """
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
