package com.gayadi.server.congestion;

import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import com.gayadi.server.congestion.dto.response.CongestionHourlyForecastResponse;
import com.gayadi.server.congestion.dto.response.CongestionHourlyPoint;

import com.gayadi.server.common.PublicDataKey;
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
    private static final int MAX_HOURLY_POINTS = 24;
    private static final List<Integer> DEFAULT_HOURLY_HOURS = List.of(9, 11, 13, 15, 17, 19);
    /** 일별 기준 점수에 더하는 시간대별 가중치. 심야는 낮추고 13-15시 방문 집중 시간대를 높인다. */
    private static final int[] HOURLY_OFFSETS = {
            -22, -24, -25, -25, -24, -22, -18, -14, -10, -6, -2, 4,
            8, 11, 13, 12, 9, 5, 1, -3, -7, -11, -15, -19,
    };

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
        if (this.serviceKey.isBlank()) {
            log.warn("관광지 집중률 API 키가 없어 혼잡도는 달력 기반 추정만 제공합니다.");
        }
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

    /**
     * 일별 기준 점수에 시간대 분포를 적용한 시간대별 예측을 반환합니다.
     * 기존 단건/묶음 예측과 에이전트 보강 로직은 그대로 두어 호환을 유지합니다.
     * 제공기관 자료는 일별 상대 집중률이므로 시간대별 값은 항상 추정치입니다.
     */
    public CongestionHourlyForecastResponse forecastHourly(
            CongestionForecastRequest request, List<Integer> hours) {
        return hourlyFrom(forecast(request), hours);
    }

    /** 이미 구한 일별 예측에 시간대 분포를 얹습니다. 같은 요청에서 일별을 다시 조회하지 않습니다. */
    public CongestionHourlyForecastResponse hourlyFrom(
            CongestionForecastResponse base, List<Integer> hours) {
        List<Integer> targetHours = normalizeHours(hours);
        List<CongestionHourlyPoint> points = targetHours.stream()
                .map(hour -> {
                    int score = Math.max(0, Math.min(100,
                            base.concentrationScore() + hourlyOffset(hour)));
                    return new CongestionHourlyPoint(hour, score, level(score));
                })
                .toList();
        return new CongestionHourlyForecastResponse(
                base.area(),
                base.placeName(),
                base.targetDate(),
                base.level(),
                base.concentrationScore(),
                base.source(),
                true,
                base.providerDataAvailable(),
                "LOW",
                "일별 예측 점수를 기준으로 시간대 분포를 적용한 추정치이므로 "
                        + "실제 혼잡과 다를 수 있습니다.",
                points);
    }

    private List<Integer> normalizeHours(List<Integer> hours) {
        if (hours == null || hours.isEmpty()) {
            return List.copyOf(DEFAULT_HOURLY_HOURS);
        }
        List<Integer> normalized = hours.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
        if (normalized.isEmpty() || normalized.size() > MAX_HOURLY_POINTS) {
            throw new IllegalArgumentException("시간대 개수는 1개 이상 24개 이하여야 합니다.");
        }
        if (normalized.stream().anyMatch(hour -> hour < 0 || hour > 23)) {
            throw new IllegalArgumentException("시간대는 0부터 23 사이여야 합니다.");
        }
        return normalized;
    }

    private int hourlyOffset(int hour) {
        return HOURLY_OFFSETS[hour];
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
        String body = response.body();
        if (body == null || body.isBlank()) {
            log.warn("관광지 집중률 빈 응답: status={} area={}{}", response.statusCode(), areaCode, districtCode);
            return null;
        }
        if (body.stripLeading().startsWith("<")) {
            log.warn("관광지 집중률 XML 오류 응답: status={} reason={}",
                    response.statusCode(), xmlTag(body, "returnReasonCode"));
            return null;
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode gateway = root.path("OpenAPI_ServiceResponse").path("cmmMsgHeader");
        if (!gateway.isMissingNode()) {
            // 공공데이터포털 게이트웨이 오류(키 미등록·활용신청 필요·트래픽 초과)
            log.warn("관광지 집중률 게이트웨이 오류: status={} reason={} {}", response.statusCode(),
                    text(gateway, "returnReasonCode"), text(gateway, "errMsg"));
            return null;
        }
        if (response.statusCode() != 200) {
            log.warn("관광지 집중률 비정상 응답: status={}", response.statusCode());
            return null;
        }
        JsonNode envelope = root.has("response") ? root.path("response") : root;
        String resultCode = text(envelope.path("header"), "resultCode");
        if (!"0000".equals(resultCode) && !"00".equals(resultCode)) {
            log.warn("관광지 집중률 업무 오류: code={} message={}",
                    resultCode, text(envelope.path("header"), "resultMsg"));
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
            log.info("관광지 집중률 자료에 기준일이 없습니다: area={}{} date={} items={}",
                    areaCode, districtCode, targetDate, items.size());
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

    private static String xmlTag(String xml, String tag) {
        int start = xml.indexOf("<" + tag + ">");
        int end = xml.indexOf("</" + tag + ">");
        return start < 0 || end <= start ? "" : xml.substring(start + tag.length() + 2, end).trim();
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
                .map(entry -> encode(entry.getKey()) + "=" + queryValue(entry.getKey(), entry.getValue()))
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

    private String queryValue(String name, String value) {
        if ("serviceKey".equals(name)) {
            return PublicDataKey.queryValue(value);
        }
        return encode(value);
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
