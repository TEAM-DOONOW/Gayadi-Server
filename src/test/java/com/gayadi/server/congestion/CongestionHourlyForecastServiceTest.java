package com.gayadi.server.congestion;

import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionHourlyForecastResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CongestionHourlyForecastServiceTest {

    private HttpServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/forecast/tatsCnctrRatedList", this::forecast);
        server.createContext("/denied/tatsCnctrRatedList",
                exchange -> respond(exchange, 403, "denied"));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shapesHourlyPointsAroundTheDailyBaseScore() {
        CongestionForecastService service = service("forecast", "test-key");

        CongestionHourlyForecastResponse result = service.forecastHourly(new CongestionForecastRequest(
                "11", "110", "서울", "", "2026-09-01T14:00:00+09:00"), List.of(3, 14));

        assertThat(result.baseScore()).isEqualTo(75);
        assertThat(result.baseLevel()).isEqualTo("CROWDED");
        assertThat(result.estimated()).isTrue();
        assertThat(result.providerDataAvailable()).isTrue();
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.points()).hasSize(2);
        assertThat(result.points().get(0).hour()).isEqualTo(3);
        assertThat(result.points().get(0).concentrationScore()).isEqualTo(50);
        assertThat(result.points().get(0).level()).isEqualTo("NORMAL");
        assertThat(result.points().get(1).hour()).isEqualTo(14);
        assertThat(result.points().get(1).concentrationScore()).isEqualTo(88);
        assertThat(result.points().get(1).level()).isEqualTo("CROWDED");
    }

    @Test
    void usesDefaultHoursWhenNoneAreRequested() {
        CongestionForecastService service = service("forecast", "test-key");

        CongestionHourlyForecastResponse result = service.forecastHourly(new CongestionForecastRequest(
                "11", "110", "서울", "", "2026-09-01T14:00:00+09:00"), null);

        assertThat(result.points()).extracting(point -> point.hour())
                .containsExactly(9, 11, 13, 15, 17, 19);
    }

    @Test
    void keepsHourlyEstimatesWhenProviderIsUnavailable() {
        CongestionForecastService service = service("denied", "test-key");

        CongestionHourlyForecastResponse result = service.forecastHourly(new CongestionForecastRequest(
                "11", "110", "서울", "경복궁", "2026-08-30T14:00:00+09:00"), List.of(14));

        assertThat(result.providerDataAvailable()).isFalse();
        assertThat(result.points()).hasSize(1);
        assertThat(result.points().getFirst().concentrationScore()).isEqualTo(83);
    }

    @Test
    void rejectsOutOfRangeHours() {
        CongestionForecastService service = service("forecast", "test-key");

        assertThatThrownBy(() -> service.forecastHourly(new CongestionForecastRequest(
                "11", "110", "서울", "", "2026-09-01T14:00:00+09:00"), List.of(24)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private CongestionForecastService service(String context, String key) {
        return new CongestionForecastService(new ObjectMapper(), key,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/" + context,
                "GayadiTest");
    }

    private void forecast(HttpExchange exchange) throws IOException {
        respond(exchange, 200, """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                  "body":{"items":{"item":[
                    {"baseYmd":"20260901","cnctrRate":"70","areaNm":"서울특별시","signguNm":"종로구","tAtsNm":"경복궁"},
                    {"baseYmd":"20260901","cnctrRate":"80","areaNm":"서울특별시","signguNm":"종로구","tAtsNm":"북촌"},
                    {"baseYmd":"20260902","cnctrRate":"20","areaNm":"서울특별시","signguNm":"종로구","tAtsNm":"경복궁"}
                  ]}}}}
                """);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
