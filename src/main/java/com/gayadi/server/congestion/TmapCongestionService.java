package com.gayadi.server.congestion;

import com.gayadi.server.congestion.model.TmapCongestionSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 전국의 지원 장소를 대상으로 TMAP 실시간·시간대별 혼잡도를 조회합니다. */
@Service
public class TmapCongestionService {

    private static final Logger log = LoggerFactory.getLogger(TmapCongestionService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter PROVIDER_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CATALOG_TTL = Duration.ofHours(24);
    private static final Duration SNAPSHOT_TTL = Duration.ofMinutes(5);

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String appKey;
    private final String baseUrl;
    private final Object catalogLock = new Object();
    private final Map<String, CachedSnapshot> snapshotCache = new ConcurrentHashMap<>();

    private volatile CachedCatalog catalogCache;

    @Autowired
    public TmapCongestionService(
            ObjectMapper objectMapper,
            @Value("${congestion.tmap.enabled:false}") boolean enabled,
            @Value("${congestion.tmap.app-key:${route.tmap.app-key:}}") String appKey,
            @Value("${congestion.tmap.base-url:https://apis.openapi.sk.com/puzzle/place}") String baseUrl) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                objectMapper, enabled, appKey, baseUrl);
    }

    TmapCongestionService(
            HttpClient client,
            ObjectMapper objectMapper,
            boolean enabled,
            String appKey,
            String baseUrl) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.appKey = appKey == null ? "" : appKey.trim();
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    /** 장소명으로 TMAP POI를 찾아 실시간 값과 지정 요일의 시간대 통계를 조회합니다. */
    public Optional<TmapCongestionSnapshot> find(
            String placeName,
            Double latitude,
            Double longitude,
            DayOfWeek dayOfWeek,
            List<Integer> hours) {
        if (!enabled || appKey.isBlank() || placeName == null || placeName.isBlank()) {
            return Optional.empty();
        }

        try {
            Optional<Poi> poi = resolvePoi(placeName);
            if (poi.isEmpty()) {
                return Optional.empty();
            }

            List<Integer> normalizedHours = normalizeHours(hours);
            String cacheKey = poi.get().id() + ":" + dayOfWeek + ":" + normalizedHours;
            CachedSnapshot cached = snapshotCache.get(cacheKey);
            if (cached != null && cached.isFresh()) {
                return Optional.of(cached.snapshot());
            }

            TmapCongestionSnapshot snapshot = fetchSnapshot(
                    poi.get(), latitude, longitude, dayOfWeek, normalizedHours);
            snapshotCache.entrySet().removeIf(entry -> !entry.getValue().isFresh());
            snapshotCache.put(cacheKey, new CachedSnapshot(snapshot, Instant.now()));
            return Optional.of(snapshot);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("TMAP 장소 혼잡도 조회가 중단되었습니다.");
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            log.warn("TMAP 장소 혼잡도 보강을 생략합니다: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<Poi> resolvePoi(String placeName) throws IOException, InterruptedException {
        Map<String, Poi> catalog = catalog();
        String normalized = normalizeName(placeName);
        Poi exact = catalog.get(normalized);
        if (exact != null) {
            return Optional.of(exact);
        }

        List<Poi> partial = catalog.entrySet().stream()
                .filter(entry -> entry.getKey().contains(normalized) || normalized.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .distinct()
                .toList();
        return partial.size() == 1 ? Optional.of(partial.getFirst()) : Optional.empty();
    }

    private Map<String, Poi> catalog() throws IOException, InterruptedException {
        CachedCatalog cached = catalogCache;
        if (cached != null && cached.isFresh()) {
            return cached.pois();
        }

        synchronized (catalogLock) {
            cached = catalogCache;
            if (cached != null && cached.isFresh()) {
                return cached.pois();
            }

            JsonNode root = get("/meta/pois", Map.of());
            Map<String, Poi> pois = new LinkedHashMap<>();
            for (JsonNode item : root.path("contents")) {
                String id = text(item, "poiId");
                String name = text(item, "poiName");
                if (!id.isBlank() && !name.isBlank()) {
                    pois.putIfAbsent(normalizeName(name), new Poi(id, name));
                }
            }
            Map<String, Poi> immutable = Map.copyOf(pois);
            catalogCache = new CachedCatalog(immutable, Instant.now());
            return immutable;
        }
    }

    private TmapCongestionSnapshot fetchSnapshot(
            Poi poi,
            Double latitude,
            Double longitude,
            DayOfWeek dayOfWeek,
            List<Integer> hours) throws IOException, InterruptedException {
        Map<String, String> realtimeQuery = new LinkedHashMap<>();
        if (latitude != null && longitude != null) {
            realtimeQuery.put("lat", latitude.toString());
            realtimeQuery.put("lng", longitude.toString());
        }

        JsonNode realtimeRoot = get("/congestion/rltm/pois/" + encode(poi.id()), realtimeQuery);
        JsonNode realtimeNode = realtimeNode(realtimeRoot.path("contents").path("rltm"));
        TmapCongestionSnapshot.Realtime realtime = new TmapCongestionSnapshot.Realtime(
                realtimeNode.path("congestion").asDouble(),
                realtimeNode.path("congestionLevel").asInt(),
                level(realtimeNode.path("congestionLevel").asInt()),
                providerDateTime(text(realtimeNode, "datetime")));

        List<TmapCongestionSnapshot.Hourly> hourly = new ArrayList<>();
        try {
            JsonNode hourlyRoot = get(
                    "/congestion/stat/hourly/pois/" + encode(poi.id()),
                    Map.of("dow", dayOfWeek.name().substring(0, 3)));
            for (JsonNode item : hourlyRoot.path("contents").path("stat")) {
                int hour = parseHour(text(item, "hh"));
                if (hours.contains(hour)) {
                    int providerLevel = item.path("congestionLevel").asInt();
                    hourly.add(new TmapCongestionSnapshot.Hourly(
                            hour,
                            item.path("congestion").asDouble(),
                            providerLevel,
                            level(providerLevel)));
                }
            }
        } catch (IOException | RuntimeException exception) {
            log.warn("TMAP 시간대별 혼잡도 보강을 생략합니다: poiId={}", poi.id());
        }
        hourly.sort(java.util.Comparator.comparingInt(TmapCongestionSnapshot.Hourly::hour));

        return new TmapCongestionSnapshot(poi.id(), poi.name(), realtime, List.copyOf(hourly));
    }

    private JsonNode get(String path, Map<String, String> query) throws IOException, InterruptedException {
        String queryString = query.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        URI uri = URI.create(baseUrl + path + (queryString.isBlank() ? "" : "?" + queryString));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("appKey", appKey)
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || response.body() == null || response.body().isBlank()) {
            throw new IllegalStateException("TMAP congestion response status=" + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        if (!"00".equals(text(root.path("status"), "code"))) {
            throw new IllegalStateException("TMAP congestion response is invalid");
        }
        return root;
    }

    private JsonNode realtimeNode(JsonNode node) {
        if (!node.isArray()) {
            return node;
        }
        for (JsonNode item : node) {
            if (item.path("type").asInt() == 1) {
                return item;
            }
        }
        return node.isEmpty() ? node : node.get(0);
    }

    private List<Integer> normalizeHours(List<Integer> hours) {
        if (hours == null || hours.isEmpty()) {
            return List.of(9, 11, 13, 15, 17, 19);
        }
        return hours.stream()
                .filter(hour -> hour != null && hour >= 0 && hour <= 23)
                .distinct()
                .sorted()
                .toList();
    }

    private OffsetDateTime providerDateTime(String value) {
        try {
            return LocalDateTime.parse(value, PROVIDER_DATE_TIME)
                    .atZone(KOREA)
                    .toOffsetDateTime();
        } catch (DateTimeParseException exception) {
            return OffsetDateTime.now(ZoneOffset.ofHours(9));
        }
    }

    private int parseHour(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private String level(int providerLevel) {
        return switch (providerLevel) {
            case 1 -> "RELAXED";
            case 2 -> "NORMAL";
            case 3 -> "CROWDED";
            default -> "VERY_CROWDED";
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.trim()
                .replaceAll("[\\s()\\[\\]{}·.,_-]", "")
                .toLowerCase(Locale.ROOT);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private record Poi(String id, String name) {
    }

    private record CachedCatalog(Map<String, Poi> pois, Instant createdAt) {
        private boolean isFresh() {
            return createdAt.plus(CATALOG_TTL).isAfter(Instant.now());
        }
    }

    private record CachedSnapshot(TmapCongestionSnapshot snapshot, Instant createdAt) {
        private boolean isFresh() {
            return createdAt.plus(SNAPSHOT_TTL).isAfter(Instant.now());
        }
    }
}
