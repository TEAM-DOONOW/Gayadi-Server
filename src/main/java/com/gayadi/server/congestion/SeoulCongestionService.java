package com.gayadi.server.congestion;

import com.gayadi.server.congestion.model.SeoulCongestionSnapshot;
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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 서울시 지정 핫스팟의 무료 실시간 인구 혼잡도를 조회합니다. */
@Service
public class SeoulCongestionService {

    private static final Logger log = LoggerFactory.getLogger(SeoulCongestionService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
    private static final Map<String, String> AREA_ALIASES = Map.of(
            "덕수궁중명전", "광화문·덕수궁",
            "중명전", "광화문·덕수궁",
            "덕수궁", "광화문·덕수궁",
            "광화문", "광화문·덕수궁");

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String apiKey;
    private final String baseUrl;
    private final Map<String, CachedSnapshot> cache = new ConcurrentHashMap<>();

    @Autowired
    public SeoulCongestionService(
            ObjectMapper objectMapper,
            @Value("${congestion.seoul.enabled:false}") boolean enabled,
            @Value("${congestion.seoul.api-key:}") String apiKey,
            @Value("${congestion.seoul.base-url:}") String baseUrl) {
        this(HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                objectMapper, enabled, apiKey, baseUrl);
    }

    SeoulCongestionService(
            HttpClient client,
            ObjectMapper objectMapper,
            boolean enabled,
            String apiKey,
            String baseUrl) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    /** 서울 장소가 지원 핫스팟이면 실시간 인구와 선택 시간대 예측을 반환합니다. */
    public Optional<SeoulCongestionSnapshot> find(
            String placeName,
            String regionName,
            String address,
            List<Integer> hours) {
        if (!isConfigured() || !isSeoul(regionName, address) || placeName == null || placeName.isBlank()) {
            return Optional.empty();
        }

        String areaName = resolveAreaName(placeName);
        List<Integer> normalizedHours = normalizeHours(hours);
        String cacheKey = areaName + ":" + normalizedHours;
        CachedSnapshot cached = cache.get(cacheKey);
        if (cached != null && cached.isFresh()) {
            return Optional.of(cached.snapshot());
        }

        try {
            SeoulCongestionSnapshot snapshot = fetch(areaName, normalizedHours);
            cache.entrySet().removeIf(entry -> !entry.getValue().isFresh());
            cache.put(cacheKey, new CachedSnapshot(snapshot, Instant.now()));
            return Optional.of(snapshot);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("서울시 실시간 인구 조회가 중단되었습니다.");
            return Optional.empty();
        } catch (IOException | RuntimeException exception) {
            log.warn("서울시 실시간 인구 보강을 생략합니다: {}", exception.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private SeoulCongestionSnapshot fetch(String areaName, List<Integer> hours)
            throws IOException, InterruptedException {
        URI uri = URI.create(baseUrl + "/" + encode(apiKey)
                + "/json/citydata_ppltn/1/5/" + encode(areaName));
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || response.body() == null || response.body().isBlank()) {
            throw new IllegalStateException("Seoul congestion response status=" + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode population = population(root);
        if (!population.isArray() || population.isEmpty()) {
            throw new IllegalStateException("Seoul congestion response is unsupported or invalid");
        }

        JsonNode item = population.get(0);
        String returnedAreaName = text(item, "AREA_NM");
        if (!normalizeName(areaName).equals(normalizeName(returnedAreaName))) {
            throw new IllegalStateException("Seoul congestion response area does not match request");
        }

        List<SeoulCongestionSnapshot.Hourly> hourly = new ArrayList<>();
        for (JsonNode forecast : item.path("FCST_PPLTN")) {
            OffsetDateTime forecastAt = dateTime(text(forecast, "FCST_TIME"));
            if (forecastAt != null && hours.contains(forecastAt.getHour())) {
                hourly.add(new SeoulCongestionSnapshot.Hourly(
                        forecastAt.getHour(),
                        level(text(forecast, "FCST_CONGEST_LVL")),
                        integer(forecast, "FCST_PPLTN_MIN"),
                        integer(forecast, "FCST_PPLTN_MAX")));
            }
        }
        hourly.sort(java.util.Comparator.comparingInt(SeoulCongestionSnapshot.Hourly::hour));

        return new SeoulCongestionSnapshot(
                returnedAreaName,
                level(text(item, "AREA_CONGEST_LVL")),
                integer(item, "AREA_PPLTN_MIN"),
                integer(item, "AREA_PPLTN_MAX"),
                dateTime(text(item, "PPLTN_TIME")),
                List.copyOf(hourly));
    }

    private JsonNode population(JsonNode root) {
        JsonNode flat = root.path("SeoulRtd.citydata_ppltn");
        if (flat.isArray()) {
            return flat;
        }
        return root.path("SeoulRtd").path("citydata_ppltn");
    }

    private boolean isConfigured() {
        return enabled && !apiKey.isBlank() && !baseUrl.isBlank();
    }

    private boolean isSeoul(String regionName, String address) {
        return containsSeoul(regionName) || containsSeoul(address);
    }

    private boolean containsSeoul(String value) {
        return value != null && (value.contains("서울") || value.toLowerCase(Locale.ROOT).contains("seoul"));
    }

    private String resolveAreaName(String placeName) {
        String normalized = normalizeName(placeName);
        return AREA_ALIASES.entrySet().stream()
                .filter(entry -> normalized.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(placeName.trim());
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

    private OffsetDateTime dateTime(String value) {
        if (value.isBlank()) {
            return null;
        }
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(value, formatter)
                        .atZone(KOREA)
                        .toOffsetDateTime();
            } catch (DateTimeParseException ignored) {
                // 공급자 응답에 사용되는 다음 날짜 형식을 계속 시도합니다.
            }
        }
        return null;
    }

    private Integer integer(JsonNode node, String field) {
        String value = text(node, field);
        try {
            return value.isBlank() ? null : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String level(String providerLevel) {
        return switch (providerLevel.replace(" ", "")) {
            case "여유" -> "RELAXED";
            case "보통" -> "NORMAL";
            case "약간붐빔" -> "CROWDED";
            case "붐빔" -> "VERY_CROWDED";
            default -> "UNKNOWN";
        };
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString();
    }

    private String normalizeName(String value) {
        return value.trim()
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

    private record CachedSnapshot(SeoulCongestionSnapshot snapshot, Instant createdAt) {

        private boolean isFresh() {
            return createdAt.plus(CACHE_TTL).isAfter(Instant.now());
        }
    }
}
