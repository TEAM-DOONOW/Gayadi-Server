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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
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
    public boolean supportsScheduledDeparture() {
        return true;
    }

    @Override
    public List<RouteEstimate> estimateSegments(List<Location> stops, String phase) {
        return estimateSegments(stops, phase, TransitRoutingOptions.defaults());
    }

    @Override
    public List<RouteEstimate> estimateSegments(
            List<Location> stops, String phase, TransitRoutingOptions options) {
        if (stops == null || stops.size() < 2) return List.of();
        try {
            List<RouteEstimate> estimates = new ArrayList<>(stops.size() - 1);
            OffsetDateTime departure = options.departureAt();
            for (int index = 0; index < stops.size() - 1; index++) {
                RouteEstimate estimate = estimate(stops.get(index), stops.get(index + 1),
                        departure, options.preference());
                estimates.add(estimate);
                departure = departure.plusMinutes((long) estimate.durationMinutes() + options.stopoverMinutes());
            }
            return List.copyOf(estimates);
        } catch (BusinessException exception) {
            // 운행 가능한 경로가 없다는 결과를 직선거리 추정으로 덮어쓰지 않습니다.
            if (!fallbackToLocal || exception.getErrorCode() == RouteErrorCode.TMAP_ROUTE_UNAVAILABLE
                    || Thread.currentThread().isInterrupted()) {
                throw exception;
            }
            return localFallback.estimateSegments(stops, phase);
        }
    }

    private RouteEstimate estimate(Location origin, Location destination,
                                   OffsetDateTime departureAt, TransitPreference preference) {
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
                departureAt.atZoneSameInstant(ZoneId.of("Asia/Seoul")).format(SEARCH_TIME));

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
            return parse(response.body(), preference);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(RouteErrorCode.TMAP_RESPONSE_INVALID);
        }
    }

    private RouteEstimate parse(String body, TransitPreference preference) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode itineraries = root.path("plan").path("itineraries");
        if (!itineraries.isArray() || itineraries.isEmpty()) {
            throw new BusinessException(RouteErrorCode.TMAP_ROUTE_UNAVAILABLE);
        }
        List<JsonNode> candidates = new ArrayList<>();
        boolean hasValidRoute = false;
        for (JsonNode candidate : itineraries) {
            double seconds = candidate.path("totalTime").asDouble(-1);
            int transfers = candidate.path("transferCount").asInt(-1);
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > Integer.MAX_VALUE || transfers < 0) continue;
            hasValidRoute = true;
            boolean unavailable = false;
            for (JsonNode leg : candidate.path("legs")) {
                if (leg.path("service").asInt(1) == 0) unavailable = true;
            }
            if (!unavailable) candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            throw new BusinessException(hasValidRoute ? RouteErrorCode.TMAP_ROUTE_UNAVAILABLE
                    : RouteErrorCode.TMAP_RESPONSE_INVALID);
        }
        Comparator<JsonNode> byTime = Comparator.comparingDouble(node -> node.path("totalTime").asDouble());
        Comparator<JsonNode> byTransfers = Comparator.comparingInt(node -> node.path("transferCount").asInt());
        Comparator<JsonNode> order = preference == TransitPreference.FEWER_TRANSFERS
                ? byTransfers.thenComparing(byTime) : byTime.thenComparing(byTransfers);
        JsonNode itinerary = candidates.stream().min(order).orElseThrow();
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
                String mode = text(leg, "mode");
                String route = text(leg, "route");
                int service = leg.path("service").asInt(1);
                String label = route.isBlank() ? mode : mode + " " + route;
                if (service == 0) {
                    label += "(운행 종료)";
                }
                if (!label.isBlank()) {
                    legs.add(label);
                }
            }
        }
        String summary = "TMAP 대중교통 경로"
                + (legs.isEmpty() ? "" : ": " + String.join(" -> ", legs));
        return new RouteEstimate(
                durationMinutes,
                transfers,
                Math.max(0, fare),
                summary,
                providerName());
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString();
    }

    private int minutes(double seconds) {
        return (int) Math.ceil(seconds / 60.0);
    }
}
