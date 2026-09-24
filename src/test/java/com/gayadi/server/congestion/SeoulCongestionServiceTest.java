package com.gayadi.server.congestion;

import com.gayadi.server.congestion.model.SeoulCongestionSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SeoulCongestionServiceTest {

    private HttpServer server;
    private AtomicInteger requests;

    @BeforeEach
    void setUp() throws IOException {
        requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::population);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void mapsSeoulRealtimePopulationAndSelectedHourlyForecasts() {
        SeoulCongestionService service = service(true);

        Optional<SeoulCongestionSnapshot> result = service.find(
                "덕수궁 중명전", "서울특별시 중구", null, List.of(9, 13, 19));

        assertThat(result).isPresent();
        SeoulCongestionSnapshot snapshot = result.orElseThrow();
        assertThat(snapshot.areaName()).isEqualTo("광화문·덕수궁");
        assertThat(snapshot.level()).isEqualTo("CROWDED");
        assertThat(snapshot.populationMin()).isEqualTo(6500);
        assertThat(snapshot.populationMax()).isEqualTo(7000);
        assertThat(snapshot.hourly()).extracting(SeoulCongestionSnapshot.Hourly::hour)
                .containsExactly(9, 13, 19);
        assertThat(requests).hasValue(1);
    }

    @Test
    void skipsNonSeoulPlacesAndDisabledProvider() {
        assertThat(service(true).find(
                "해운대해수욕장", "부산광역시", null, List.of(9))).isEmpty();
        assertThat(service(false).find(
                "덕수궁", "서울특별시", null, List.of(9))).isEmpty();
        assertThat(requests).hasValue(0);
    }

    @Test
    void rejectsSampleResponseWhenReturnedAreaDoesNotMatchRequestedPlace() {
        assertThat(service(true).find(
                "서울숲", "서울특별시", null, List.of(9))).isEmpty();
        assertThat(requests).hasValue(1);
    }

    private SeoulCongestionService service(boolean enabled) {
        return new SeoulCongestionService(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                enabled,
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void population(HttpExchange exchange) throws IOException {
        requests.incrementAndGet();
        respond(exchange, """
                {
                  "SeoulRtd.citydata_ppltn": [{
                      "AREA_NM": "광화문·덕수궁",
                      "AREA_CONGEST_LVL": "약간 붐빔",
                      "AREA_PPLTN_MIN": "6500",
                      "AREA_PPLTN_MAX": "7000",
                      "PPLTN_TIME": "2026-09-24 10:10",
                      "FCST_PPLTN": [
                        {"FCST_TIME": "2026-09-24 09:00", "FCST_CONGEST_LVL": "보통",
                         "FCST_PPLTN_MIN": "5000", "FCST_PPLTN_MAX": "5500"},
                        {"FCST_TIME": "2026-09-24 13:00", "FCST_CONGEST_LVL": "붐빔",
                         "FCST_PPLTN_MIN": "8000", "FCST_PPLTN_MAX": "8500"},
                        {"FCST_TIME": "2026-09-24 19:00", "FCST_CONGEST_LVL": "여유",
                         "FCST_PPLTN_MIN": "3000", "FCST_PPLTN_MAX": "3500"}
                      ]
                    }],
                  "RESULT": {
                    "RESULT.CODE": "INFO-000",
                    "RESULT.MESSAGE": "정상 처리되었습니다."
                  }
                }
                """);
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
