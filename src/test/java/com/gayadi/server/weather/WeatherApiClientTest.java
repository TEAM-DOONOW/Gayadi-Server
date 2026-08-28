package com.gayadi.server.weather;

import com.gayadi.server.common.ApiException;
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
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherApiClientTest {

    private HttpServer server;
    private AtomicInteger pageRequests;

    @BeforeEach
    void setUp() throws IOException {
        pageRequests = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/weather/pages", this::pages);
        server.createContext("/weather/xml", exchange -> respond(exchange, 200, """
                <OpenAPI_ServiceResponse><cmmMsgHeader>
                  <returnReasonCode>30</returnReasonCode>
                  <returnAuthMsg>SERVICE KEY IS NOT REGISTERED ERROR.</returnAuthMsg>
                </cmmMsgHeader></OpenAPI_ServiceResponse>
                """));
        server.createContext("/weather/limited", exchange -> respond(exchange, 429, "rate limited"));
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void collectsEveryPageUntilTotalCountIsReached() {
        WeatherApiClient client = client("pages", "test-key");
        List<tools.jackson.databind.JsonNode> items = client.allItems("", client.baseParams());

        assertThat(items).extracting(item -> item.path("category").asText())
                .containsExactly("TMP", "SKY", "PTY");
        assertThat(pageRequests).hasValue(2);
    }

    @Test
    void convertsXmlAuthenticationAndRateLimitResponses() {
        assertThatThrownBy(() -> client("xml", "test-key").call("", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("SERVICE KEY IS NOT REGISTERED");
        assertThatThrownBy(() -> client("limited", "test-key").call("", Map.of()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("호출 한도");
    }

    @Test
    void rejectsMissingServiceKeyBeforeSendingARequest() {
        assertThatThrownBy(() -> client("pages", "").baseParams())
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("WEATHER_API_KEY");
    }

    private WeatherApiClient client(String context, String key) {
        return new WeatherApiClient(new ObjectMapper(), key,
                "http://127.0.0.1:" + server.getAddress().getPort() + "/weather/" + context);
    }

    private void pages(HttpExchange exchange) throws IOException {
        pageRequests.incrementAndGet();
        boolean secondPage = exchange.getRequestURI().getRawQuery().contains("pageNo=2");
        String items = secondPage
                ? "{\"category\":\"PTY\"}"
                : "{\"category\":\"TMP\"},{\"category\":\"SKY\"}";
        int page = secondPage ? 2 : 1;
        respond(exchange, 200, """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL_SERVICE"},
                  "body":{"pageNo":%d,"numOfRows":2,"totalCount":3,
                  "items":{"item":[%s]}}}}
                """.formatted(page, items));
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
