package com.gayadi.server.congestion;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import com.gayadi.server.common.ApiException;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CongestionForecastServiceTest {

    private HttpServer server;
    private AtomicReference<String> query;
    private AtomicInteger requestCount;

    @BeforeEach
    void setUp() throws IOException {
        query = new AtomicReference<>();
        requestCount = new AtomicInteger();
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
    void averagesProviderRatesForTheRequestedDateAndNormalizesDistrictCode() {
        CongestionForecastService service = service("forecast", "test-key");

        CongestionForecast result = service.forecast(new CongestionForecastService.Request(
                "11", "110", "서울", "", "2026-09-01T14:00:00+09:00"));

        assertThat(result.source()).isEqualTo("KTO_DISTRICT_CONCENTRATION_FORECAST");
        assertThat(result.concentrationScore()).isEqualTo(75);
        assertThat(result.level()).isEqualTo("CROWDED");
        assertThat(result.providerDataAvailable()).isTrue();
        assertThat(query.get()).contains("areaCd=11", "signguCd=11110");
    }

    @Test
    void labelsCalendarFallbackWhenProviderIsUnavailable() {
        CongestionForecastService service = service("denied", "test-key");

        CongestionForecast result = service.forecast(new CongestionForecastService.Request(
                "11", "110", "서울", "경복궁", "2026-08-30T14:00:00+09:00"));

        assertThat(result.source()).isEqualTo("CALENDAR_HEURISTIC");
        assertThat(result.confidence()).isEqualTo("LOW");
        assertThat(result.providerDataAvailable()).isFalse();
        assertThat(result.concentrationScore()).isEqualTo(70);
        assertThat(result.message()).contains("공공 예측 자료");
    }

    @Test
    void rejectsInvalidTargetDateInsteadOfReturningServerError() {
        CongestionForecastService service = service("forecast", "");

        assertThatThrownBy(() -> service.forecast(new CongestionForecastService.Request(
                "11", "110", "서울", "", "not-a-date")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ISO-8601");
    }

    @Test
    void batchUsesOneDistrictRequestAndDistinguishesExactFromDistrictAverage() {
        CongestionForecastService service = service("forecast", "test-key");

        List<CongestionForecast> results = service.forecastAll(List.of(
                new CongestionForecastService.Request(
                        "11", "110", "서울", "경복궁", "2026-09-01T14:00:00+09:00"),
                new CongestionForecastService.Request(
                        "11", "110", "서울", "알 수 없는 장소", "2026-09-01T14:00:00+09:00")));

        assertThat(requestCount.get()).isEqualTo(1);
        assertThat(results.get(0).concentrationScore()).isEqualTo(70);
        assertThat(results.get(0).source()).isEqualTo("KTO_TOURIST_CONCENTRATION_FORECAST");
        assertThat(results.get(1).concentrationScore()).isEqualTo(75);
        assertThat(results.get(1).source()).isEqualTo("KTO_DISTRICT_CONCENTRATION_FORECAST");
    }

    @Test
    void reusesTheSameDistrictAndDateSnapshotAcrossCalls() {
        CongestionForecastService service = service("forecast", "test-key");
        CongestionForecastService.Request request = new CongestionForecastService.Request(
                "11", "110", "서울", "경복궁", "2026-09-01T14:00:00+09:00");

        service.forecast(request);
        service.forecast(request);

        assertThat(requestCount.get()).isEqualTo(1);
    }

    private CongestionForecastService service(String context, String key) {
        return new CongestionForecastService(new ObjectMapper(), new CongestionApiProperties(
                key, "http://127.0.0.1:" + server.getAddress().getPort() + "/" + context,
                "GayadiTest", java.time.Duration.ofSeconds(5), java.time.Duration.ofSeconds(10),
                java.time.Duration.ofMinutes(30), 512));
    }

    private void forecast(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        query.set(exchange.getRequestURI().getRawQuery());
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
