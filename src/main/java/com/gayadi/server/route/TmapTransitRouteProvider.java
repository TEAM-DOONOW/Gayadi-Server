package com.gayadi.server.route;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** SK Open API TMAP 대중교통 경로를 RouteProvider 계약으로 변환합니다. */
@Component
@Primary
@ConditionalOnProperty(name = "route.provider", havingValue = "tmap")
public class TmapTransitRouteProvider implements RouteProvider {

    private static final DateTimeFormatter SEARCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String appKey;
    private final String baseUrl;
    private final boolean fallbackToLocal;
    private final LocalRouteProvider localFallback;
    private final Duration requestTimeout;
    private final int maximumResults;

    @Autowired
    public TmapTransitRouteProvider(
            ObjectMapper objectMapper,
            TmapProperties properties,
            LocalRouteProvider localFallback) {
        this.client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        this.objectMapper = objectMapper;
        this.appKey = properties.appKey() == null ? "" : properties.appKey().trim();
        this.baseUrl = properties.baseUrl().trim();
        this.fallbackToLocal = properties.fallbackToLocal();
        this.localFallback = localFallback;
        this.requestTimeout = properties.requestTimeout();
        this.maximumResults = properties.maximumResults();
    }

    @Override
    public String providerName() {
        return TMAP_TRANSIT;
    }

    @Override
    public List<RouteEstimate> estimateSegments(List<Location> stops, String phase) {
        if (stops == null || stops.size() < 2) return List.of();
        try {
            List<RouteEstimate> estimates = new ArrayList<>(stops.size() - 1);
            for (int index = 0; index < stops.size() - 1; index++) {
                estimates.add(estimate(stops.get(index), stops.get(index + 1)));
            }
            return List.copyOf(estimates);
        } catch (ApiException exception) {
            if (!fallbackToLocal) throw exception;
            return localFallback.estimateSegments(stops, phase);
        }
    }

    private RouteEstimate estimate(Location origin, Location destination) {
        if (appKey == null || appKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TMAP 대중교통 API 키(SKT_APPKEY)가 설정되지 않았습니다.");
        }

        String body = """
                {
                  "startX": "%s",
                  "startY": "%s",
                  "endX": "%s",
                  "endY": "%s",
                  "lang": 0,
                  "format": "json",
                  "count": %d,
                  "searchDttm": "%s"
                }
                """.formatted(origin.longitude(), origin.latitude(),
                destination.longitude(), destination.latitude(), maximumResults,
                LocalDateTime.now().format(SEARCH_TIME));

        HttpResponse<String> response;
        try {
            response = client.send(HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(requestTimeout)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("appKey", appKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMAP 대중교통 API 호출이 중단되었습니다.");
        } catch (IOException | IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "TMAP 대중교통 API 호출에 실패했습니다.");
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "TMAP 대중교통 API 인증에 실패했습니다.");
        }
        if (response.statusCode() == 429) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "TMAP 대중교통 API 호출 한도를 초과했습니다.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "TMAP 대중교통 API가 오류를 반환했습니다.");
        }

        try {
            return parse(response.body());
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "TMAP 대중교통 API 응답을 해석하지 못했습니다.");
        }
    }

    private RouteEstimate parse(String body) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode itineraries = root.path("plan").path("itineraries");
        if (!itineraries.isArray() || itineraries.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "이동 가능한 TMAP 대중교통 경로가 없습니다.");
        }
        JsonNode itinerary = itineraries.get(0);
        int durationMinutes = minutes(itinerary.path("totalTime").asDouble(0));
        int transfers = Math.max(0, itinerary.path("transferCount").asInt(0));
        int fare = itinerary.path("fare").path("regular").path("totalFare").asInt(0);
        if (durationMinutes <= 0) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "TMAP 대중교통 API가 유효한 소요시간을 반환하지 않았습니다.");
        }

        List<String> legs = new ArrayList<>();
        JsonNode legNodes = itinerary.path("legs");
        if (legNodes.isArray()) {
            for (JsonNode leg : legNodes) {
                String mode = leg.path("mode").asText("");
                String route = leg.path("route").asText("");
                int service = leg.path("service").asInt(1);
                String label = route.isBlank() ? mode : mode + " " + route;
                if (service == 0) label += "(운행 종료)";
                if (!label.isBlank()) legs.add(label);
            }
        }
        String summary = "TMAP 대중교통 경로"
                + (legs.isEmpty() ? "" : ": " + String.join(" -> ", legs));
        return new RouteEstimate(
                durationMinutes, transfers, Math.max(0, fare), summary, providerName());
    }

    private int minutes(double seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }
}
