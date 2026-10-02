package com.gayadi.server.congestion;

import com.gayadi.server.congestion.model.TmapCongestionSnapshot;
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
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TmapCongestionServiceTest {

    private HttpServer server;
    private AtomicInteger requests;

    @BeforeEach
    void setUp() throws IOException {
        requests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/meta/pois", exchange -> respond(exchange, """
                {"status":{"code":"00"},"contents":[
                  {"poiId":"1172091","poiName":"경복궁"}
                ]}
                """));
        server.createContext("/congestion/rltm/pois/1172091", exchange -> respond(exchange, """
                {"status":{"code":"00"},"contents":{"rltm":{
                  "congestion":0.03126,"congestionLevel":3,"datetime":"20260924101000"
                }}}
                """));
        server.createContext("/congestion/stat/hourly/pois/1172091", exchange -> respond(exchange, """
                {"status":{"code":"00"},"contents":{"stat":[
                  {"hh":"09","congestion":0.02,"congestionLevel":2},
                  {"hh":"13","congestion":0.04,"congestionLevel":4},
                  {"hh":"19","congestion":0.01,"congestionLevel":1}
                ]}}
                """));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void mapsRealtimeAndSelectedSameWeekdayStatistics() {
        TmapCongestionService service = service(true);

        Optional<TmapCongestionSnapshot> result = service.find(
                "경복궁", 37.5796, 126.977, DayOfWeek.THURSDAY, List.of(9, 19));

        assertThat(result).isPresent();
        TmapCongestionSnapshot snapshot = result.orElseThrow();
        assertThat(snapshot.poiId()).isEqualTo("1172091");
        assertThat(snapshot.realtime().level()).isEqualTo("CROWDED");
        assertThat(snapshot.realtime().densityPerSquareMeter()).isEqualTo(0.03126);
        assertThat(snapshot.hourly()).extracting(TmapCongestionSnapshot.Hourly::hour)
                .containsExactly(9, 19);
        assertThat(requests).hasValue(3);
    }

    @Test
    void skipsRequestsWhenPaidProviderIsDisabled() {
        assertThat(service(false).find(
                "경복궁", 37.5796, 126.977, DayOfWeek.THURSDAY, List.of(9))).isEmpty();
        assertThat(requests).hasValue(0);
    }

    private TmapCongestionService service(boolean enabled) {
        return new TmapCongestionService(
                HttpClient.newHttpClient(),
                new ObjectMapper(),
                enabled,
                "test-key",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }

    private void respond(HttpExchange exchange, String body) throws IOException {
        requests.incrementAndGet();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
