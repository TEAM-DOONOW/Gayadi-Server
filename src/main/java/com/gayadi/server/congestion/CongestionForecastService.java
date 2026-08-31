package com.gayadi.server.congestion;

import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;

import com.gayadi.server.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;

/** 공공데이터와 대체 추정을 이용해 관광지 혼잡도를 계산합니다. */
@Service
public class CongestionForecastService {

    private static final Logger log = LoggerFactory.getLogger(CongestionForecastService.class);
    private static final String OPERATION = "tatsCnctrRatedList";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Duration SNAPSHOT_CACHE_TTL = Duration.ofMinutes(30);
    private static final int MAX_SNAPSHOT_CACHE_ENTRIES = 512;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper;
    private final String serviceKey;
    private final String baseUrl;
    private final String mobileApp;
    private final Map<GroupKey, CachedSnapshot> snapshotCache = new ConcurrentHashMap<>();
    private final Map<GroupKey, Object> snapshotLocks = new ConcurrentHashMap<>();

    public CongestionForecastService(
            ObjectMapper objectMapper,
            @Value("${congestion.api.key:}") String serviceKey,
            @Value("${congestion.api.base-url:https://apis.data.go.kr/B551011/TatsCnctrRateService}") String baseUrl,
            @Value("${congestion.api.mobile-app:Gayadi}") String mobileApp) {
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.mobileApp = mobileApp == null || mobileApp.isBlank() ? "Gayadi" : mobileApp.trim();
    }

    /** 지역과 날짜 조건으로 관광지 혼잡도를 예측합니다. */
    public CongestionForecastResponse forecast(CongestionForecastRequest request) {
        return forecastAll(List.of(request)).getFirst();
    }

    /** 같은 시군구의 공공 예측 자료를 한 번만 조회해 여러 장소를 보강한다. */
    public List<CongestionForecastResponse> forecastAll(List<CongestionForecastRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        Map<GroupKey, List<IndexedRequest>> groups = new LinkedHashMap<>();
        for (int index = 0; index < requests.size(); index++) {
            CongestionForecastRequest request = requests.get(index);
            String areaCode = normalizeCode(request.areaCode());
            String districtCode = normalizeDistrict(areaCode, request.districtCode());
            LocalDate date = targetDate(request.targetAt());
            groups.computeIfAbsent(
                            new GroupKey(
                                    areaCode,
                                    districtCode,
                                    date),
                            ignored -> new ArrayList<>())
                    .add(new IndexedRequest(index, request));
        }
        CongestionForecastResponse[] result = new CongestionForecastResponse[requests.size()];
        for (Map.Entry<GroupKey, List<IndexedRequest>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            ProviderSnapshot snapshot = null;
            if (!serviceKey.isBlank() && key.areaCode().matches("\\d{2}")
                    && key.districtCode().matches("\\d{5}")) {
                try {
                    snapshot = cachedSnapshot(key);
                } catch (Exception exception) {
                    log.warn("관광지 집중률 묶음 보강 생략: {}", exception.getClass().getSimpleName());
                }
            }
            for (IndexedRequest indexed : entry.getValue()) {
                CongestionForecastRequest request = indexed.request();
                result[indexed.index()] = snapshot == null
                        ? heuristic(request.areaName(), request.placeName(), key.targetDate(), request.targetAt())
                        : snapshot.forecast(request.placeName());
            }
        }
        return List.of(result);
    }

    private ProviderSnapshot cachedSnapshot(GroupKey key) throws Exception {
        CachedSnapshot cached = snapshotCache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.snapshot();
        }
        Object lock = snapshotLocks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                cached = snapshotCache.get(key);
                if (cached != null && cached.isFresh()) {
                    return cached.snapshot();
                }
                ProviderSnapshot snapshot = providerSnapshot(
                        key.areaCode(), key.districtCode(), key.targetDate());
                if (snapshot != null) {
                    trimSnapshotCache();
                    snapshotCache.put(
                            key,
                            new CachedSnapshot(
                                    snapshot,
                                    Instant.now()));
                }
                return snapshot;
            }
        } finally {
            snapshotLocks.remove(key, lock);
        }
    }

    private void trimSnapshotCache() {
        snapshotCache.entrySet().removeIf(entry -> !entry.getValue().isFresh());
        if (snapshotCache.size() < MAX_SNAPSHOT_CACHE_ENTRIES) {
            return;
        }
        snapshotCache.entrySet().stream()
                .min(Map.Entry.comparingByValue((left, right) -> left.createdAt().compareTo(right.createdAt())))
                .ifPresent(entry -> snapshotCache.remove(entry.getKey(), entry.getValue()));
    }

    private ProviderSnapshot providerSnapshot(
            String areaCode, String districtCode, LocalDate targetDate) throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("serviceKey", serviceKey);
        params.put("pageNo", "1");
        params.put("numOfRows", "1000");
        params.put("MobileOS", "ETC");
        params.put("MobileApp", mobileApp);
        params.put("areaCd", areaCode);
        params.put("signguCd", districtCode);
        params.put("_type", "json");

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(buildUri(params)).timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body() == null || response.body().isBlank()
                || response.body().stripLeading().startsWith("<")) return null;
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode envelope = root.has("response") ? root.path("response") : root;
        String resultCode = text(envelope.path("header"), "resultCode");
        if (!"0000".equals(resultCode) && !"00".equals(resultCode)) {
            return null;
        }

        JsonNode itemNode = envelope.path("body").path("items").path("item");
        List<JsonNode> items = new ArrayList<>();
        if (itemNode.isArray()) {
            itemNode.forEach(items::add);
        }
        else if (itemNode.isObject()) items.add(itemNode);
        List<JsonNode> targetItems = items.stream()
                .filter(item -> targetDate.equals(parseDate(text(item, "baseYmd"))))
                .filter(item -> Double.isFinite(rate(item)))
                .toList();
        if (targetItems.isEmpty()) {
            return null;
        }
        int average = (int) Math.round(targetItems.stream().mapToDouble(this::rate).average().orElseThrow());
        Map<String, Integer> scores = targetItems.stream()
                .filter(item -> !normalize(text(item, "tAtsNm")).isBlank())
                .collect(Collectors.toMap(
                        item -> normalizeName(text(item, "tAtsNm")),
                        item -> (int) Math.round(Math.max(0, Math.min(100, rate(item)))),
                        (left, right) -> (left + right) / 2,
                        LinkedHashMap::new));
        JsonNode representative = targetItems.getFirst();
        return new ProviderSnapshot(targetDate,
                joinArea(
                        text(representative, "areaNm"),
                        text(representative, "signguNm")),
                Math.max(0, Math.min(100, average)), Map.copyOf(scores));
    }

    private CongestionForecastResponse heuristic(
            String areaName, String placeName, LocalDate date, String targetAt) {
        DayOfWeek day = date.getDayOfWeek();
        int score = day == DayOfWeek.SATURDAY ? 70
                : day == DayOfWeek.SUNDAY ? 60 : 45;
        Integer hour = targetHour(targetAt);
        if (hour != null && hour >= 11 && hour <= 17) {
            score += 10;
        }
        score = Math.min(100, score);
        return new CongestionForecastResponse(
                level(score),
                score,
                normalize(areaName),
                normalize(placeName),
                date,
                "CALENDAR_HEURISTIC",
                true,
                false,
                "LOW",
                "공공 예측 자료를 사용할 수 없어 요일과 시간대만으로 추정했습니다.");
    }

    private URI buildUri(Map<String, String> params) {
        String query = params.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
        return URI.create(baseUrl + "/" + OPERATION + "?" + query);
    }

    private LocalDate targetDate(String targetAt) {
        if (targetAt == null || targetAt.isBlank()) {
            return LocalDate.now(KOREA);
        }
        try {
            return OffsetDateTime.parse(targetAt).atZoneSameInstant(KOREA).toLocalDate();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CongestionErrorCode.CONGESTION_TARGET_AT_INVALID);
        }
    }

    private Integer targetHour(String targetAt) {
        if (targetAt == null || targetAt.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(targetAt).atZoneSameInstant(KOREA).getHour();
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CongestionErrorCode.CONGESTION_TARGET_AT_INVALID);
        }
    }

    private LocalDate parseDate(String value) {
        if (value == null || !value.matches("\\d{8}")) {
            return null;
        }
        return LocalDate.of(Integer.parseInt(value.substring(0, 4)),
                Integer.parseInt(value.substring(4, 6)), Integer.parseInt(value.substring(6, 8)));
    }

    private double rate(JsonNode item) {
        try {
            return Double.parseDouble(text(item, "cnctrRate", "NaN"));
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private String level(int score) {
        if (score < 40) {
            return "RELAXED";
        }
        if (score < 70) {
            return "NORMAL";
        }
        return "CROWDED";
    }

    private String text(JsonNode node, String field) {
        return text(node, field, "");
    }

    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull()
                ? defaultValue
                : value.asString();
    }

    private String normalizeDistrict(String areaCode, String districtCode) {
        String district = normalizeCode(districtCode);
        if (district.matches("\\d{3}") && areaCode.matches("\\d{2}")) {
            return areaCode + district;
        }
        return district;
    }

    private String normalizeCode(String value) {
        return normalize(value).replaceAll("[^0-9]", "");
    }

    private String normalizeName(String value) {
        return normalize(value).replaceAll("[\\s()\\[\\]{}·.,_-]", "").toLowerCase();
    }

    private String joinArea(String area, String district) {
        if (area.isBlank()) {
            return district;
        }
        if (district.isBlank()) {
            return area;
        }
        return area + " " + district;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripTrailingSlash(String value) {
        String result = normalize(value);
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record GroupKey(
            String areaCode,
            String districtCode,
            LocalDate targetDate
    ) {
    }

    private record IndexedRequest(
            int index,
            CongestionForecastRequest request
    ) {
    }

    private record CachedSnapshot(
            ProviderSnapshot snapshot,
            Instant createdAt
    ) {
        private boolean isFresh() {
            return createdAt.plus(SNAPSHOT_CACHE_TTL).isAfter(Instant.now());
        }
    }

    private record ProviderSnapshot(
            LocalDate targetDate,
            String area,
            int districtAverage,
            Map<String, Integer> scores
    ) {
        private CongestionForecastResponse forecast(String placeName) {
            String normalized = placeName == null ? "" : placeName.trim()
                    .replaceAll("[\\s()\\[\\]{}·.,_-]", "").toLowerCase();
            Integer exact = scores.get(normalized);
            int score = Objects.requireNonNullElse(exact, districtAverage);
            boolean placeMatched = exact != null;
            return new CongestionForecastResponse(
                    score < 40 ? "RELAXED" : score < 70 ? "NORMAL" : "CROWDED",
                    score, area, placeName == null ? "" : placeName.trim(), targetDate,
                    placeMatched ? "KTO_TOURIST_CONCENTRATION_FORECAST"
                            : "KTO_DISTRICT_CONCENTRATION_FORECAST",
                    true, true, placeMatched ? "MEDIUM" : "LOW",
                    placeMatched
                            ? "2018년 이후 이동통신 방문 패턴을 기반으로 한 향후 30일 상대 집중률입니다."
                            : "해당 장소의 직접 예측값이 없어 같은 시군구 관광지의 평균 집중률을 적용했습니다.");
        }
    }
}
