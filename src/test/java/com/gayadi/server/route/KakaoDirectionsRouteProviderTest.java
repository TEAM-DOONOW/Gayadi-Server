package com.gayadi.server.route;

import com.gayadi.server.common.Location;
import com.gayadi.server.common.exception.BusinessException;
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

class KakaoDirectionsRouteProviderTest {
    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> query = new AtomicReference<>();
    private final List<Location> stops = List.of(
            new Location("A", 37.1, 127.1), new Location("B", 37.2, 127.2));

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/directions";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void requestsTimePriorityAndReturnsMinutesZeroTransfersAndToll() {
        respond(200, """
                {"routes":[{"result_code":0,"summary":{"duration":1250,
                "fare":{"taxi":15000,"toll":900}}}]}
                """);
        RouteProvider provider = new KakaoDirectionsRouteProvider(new ObjectMapper(), "placeholder", baseUrl);
        var result = provider.estimateSegments(stops, "IN_TRIP");
        assertThat(provider.transportMode()).isEqualTo(TransportMode.CAR);
        assertThat(query.get()).contains("origin=127.1,37.1", "destination=127.2,37.2", "priority=TIME");
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().durationMinutes()).isEqualTo(21);
        assertThat(result.getFirst().transferCount()).isZero();
        assertThat(result.getFirst().fare()).isEqualTo(900);
        assertThat(result.getFirst().providerName()).isEqualTo("KAKAO_DIRECTIONS");
    }

    @Test
    void missingConfigurationDoesNotFallBackToTransit() {
        var provider = new KakaoDirectionsRouteProvider(new ObjectMapper(), "", baseUrl);
        assertFailure(provider, RouteErrorCode.KAKAO_NOT_CONFIGURED);
    }

    @Test
    void reportsRateLimitWithoutReturningPartialEstimates() {
        respond(429, "{}");
        assertFailure(provider(), RouteErrorCode.KAKAO_RATE_LIMITED);
    }

    @Test
    void rejectsUnavailableRoute() {
        respond(200, "{\"routes\":[{\"result_code\":104}]}");
        assertFailure(provider(), RouteErrorCode.KAKAO_ROUTE_UNAVAILABLE);
    }

    @Test
    void rejectsMalformedResponse() {
        respond(200, "{\"routes\":[{\"result_code\":0,\"summary\":{}}]}");
        assertFailure(provider(), RouteErrorCode.ROUTE_PROVIDER_FAILED);
    }

    private KakaoDirectionsRouteProvider provider() {
        return new KakaoDirectionsRouteProvider(new ObjectMapper(), "placeholder", baseUrl);
    }

    private void assertFailure(KakaoDirectionsRouteProvider provider, RouteErrorCode error) {
        assertThatThrownBy(() -> provider.estimateSegments(stops, "IN_TRIP"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(error));
    }

    private void respond(int status, String body) {
        server.createContext("/directions", exchange -> {
            query.set(exchange.getRequestURI().getQuery());
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            exchange.close();
        });
    }
}
