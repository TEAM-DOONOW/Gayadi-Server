package com.gayadi.server.route;

import com.gayadi.server.common.Location;
import com.gayadi.server.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
@ConditionalOnProperty(name = "route.provider", havingValue = "tmap")
public class TmapTransitRouteProvider implements RouteProvider {

    private static final DateTimeFormatter SEARCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final int MAX_RESULTS = 10;

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String appKey;
    private final String baseUrl;
    private final boolean fallbackToLocal;
    private final LocalRouteProvider localFallback = new LocalRouteProvider();

    @Autowired
    public TmapTransitRouteProvider(
            ObjectMapper objectMapper,
            @Value("${route.tmap.app-key:}") String appKey,
            @Value("${route.tmap.base-url:https://apis.openapi.sk.com/transit/routes}") String baseUrl,
            @Value("${route.tmap.fallback-to-local:true}") boolean fallbackToLocal) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        this.appKey = appKey;
        this.baseUrl = baseUrl;
        this.fallbackToLocal = fallbackToLocal;
    }

    public TmapTransitRouteProvider(ObjectMapper objectMapper, String appKey, String baseUrl) {
        this(objectMapper, appKey, baseUrl, false);
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
        } catch (BusinessException exception) {
            if (!fallbackToLocal) throw exception;
            return localFallback.estimateSegments(stops, phase);
        }
    }

    private RouteEstimate estimate(Location origin, Location destination) {
        if (appKey == null || appKey.isBlank()) {
            throw new BusinessException(RouteErrorCode.TMAP_NOT_CONFIGURED);
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
                destination.longitude(), destination.latitude(), MAX_RESULTS,
                LocalDateTime.now().format(SEARCH_TIME));

        HttpResponse<String> response;
        try {
            response = client.send(HttpRequest.newBuilder(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("appKey", appKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(RouteErrorCode.TMAP_REQUEST_FAILED);
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(RouteErrorCode.TMAP_REQUEST_FAILED);
        }

        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new BusinessException(RouteErrorCode.TMAP_AUTH_FAILED);
        }
        if (response.statusCode() == 429) {
            throw new BusinessException(RouteErrorCode.TMAP_RATE_LIMITED);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(RouteErrorCode.TMAP_RESPONSE_INVALID);
        }

        try {
            return parse(response.body());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(RouteErrorCode.TMAP_RESPONSE_INVALID);
        }
    }

    private RouteEstimate parse(String body) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode itineraries = root.path("plan").path("itineraries");
        if (!itineraries.isArray() || itineraries.isEmpty()) {
            throw new BusinessException(RouteErrorCode.TMAP_ROUTE_UNAVAILABLE);
        }
        JsonNode itinerary = itineraries.get(0);
        int durationMinutes = minutes(itinerary.path("totalTime").asDouble(0));
        int transfers = Math.max(0, itinerary.path("transferCount").asInt(0));
        int fare = itinerary.path("fare").path("regular").path("totalFare").asInt(0);
        if (durationMinutes <= 0) {
            throw new BusinessException(RouteErrorCode.TMAP_RESPONSE_INVALID);
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
