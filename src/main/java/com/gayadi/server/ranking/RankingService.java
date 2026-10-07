package com.gayadi.server.ranking;

import com.gayadi.server.ranking.RankingRepository.FavoriteRankRow;
import com.gayadi.server.ranking.dto.response.RankingItemResponse;
import com.gayadi.server.ranking.dto.response.RankingResponse;
import com.gayadi.server.tourapi.TourApiService;
import com.gayadi.server.tourapi.TourRegionResolver;
import com.gayadi.server.tourapi.TourRegionResolver.RegionCode;
import com.gayadi.server.tourapi.dto.request.AreaBasedListRequest;
import com.gayadi.server.tourapi.dto.request.FestivalSearchRequest;
import com.gayadi.server.tourapi.dto.request.KeywordSearchRequest;
import com.gayadi.server.tourapi.dto.response.TourPlaceResponse;
import com.gayadi.server.tourapi.model.LegalDistrict;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 홈 화면 카테고리별 TOP 순위를 계산합니다. */
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M.d");
    private static final String DEFAULT_ATTRACTION_REGION = "서울";
    /** 중심 관광지 목록에서 관광지 순위와 섞지 않을 분류(맛집은 찜 순위로 따로 제공). */
    private static final List<String> EXCLUDED_HUB_CATEGORIES = List.of("음식", "숙박");
    /** 광역 단위 조회가 비었을 때 시군구별로 나눠 조회하는 최대 횟수. */
    private static final int MAX_DISTRICT_CALLS = 30;
    private static final int MAX_CACHE_ENTRIES = 256;

    private final TourApiService tourApi;
    private final TourRegionResolver regionResolver;
    private final DataLabClient dataLab;
    private final RankingRepository repository;
    private final Clock clock;
    private final Map<CacheKey, Cached> cache = new ConcurrentHashMap<>();

    @Autowired
    public RankingService(
            TourApiService tourApi, TourRegionResolver regionResolver,
            DataLabClient dataLab, RankingRepository repository) {
        this(tourApi, regionResolver, dataLab, repository, Clock.system(KOREA));
    }

    RankingService(
            TourApiService tourApi, TourRegionResolver regionResolver,
            DataLabClient dataLab, RankingRepository repository, Clock clock) {
        this.tourApi = tourApi;
        this.regionResolver = regionResolver;
        this.dataLab = dataLab;
        this.repository = repository;
        this.clock = clock;
    }

    /** 순위 종류와 지역으로 상위 목록을 조회합니다. 지역이 비면 전국 기준입니다(관광지는 서울). */
    public RankingResponse rank(RankingType type, String region, int limit) {
        String normalizedRegion = region == null ? "" : region.trim();
        if (type == RankingType.ATTRACTION && normalizedRegion.isEmpty()) {
            normalizedRegion = DEFAULT_ATTRACTION_REGION;
        }
        CacheKey key = new CacheKey(type, normalizedRegion, limit);
        Cached cached = cache.get(key);
        Instant now = clock.instant();
        if (cached != null && cached.isFresh(now)) {
            return cached.response();
        }
        RankingResponse response = switch (type) {
            case ATTRACTION -> attractions(normalizedRegion, limit);
            case FESTIVAL -> festivals(normalizedRegion, limit);
            case REGION -> regions(normalizedRegion, limit);
            case RESTAURANT -> restaurants(normalizedRegion, limit);
        };
        // 제공기관 자료가 없어 대체한 결과는 짧게만 보관해 승인·장애 복구가 빨리 반영되게 합니다.
        Duration ttl = !response.providerDataAvailable() ? Duration.ofMinutes(10) : ttl(type);
        trimCache(now);
        cache.put(key, new Cached(response, now.plus(ttl)));
        return response;
    }

    private static Duration ttl(RankingType type) {
        return switch (type) {
            case ATTRACTION, REGION -> Duration.ofHours(12);
            case FESTIVAL -> Duration.ofHours(1);
            case RESTAURANT -> Duration.ofMinutes(5);
        };
    }

    // ---- 관광지: 데이터랩 중심 관광지 ----

    private RankingResponse attractions(String region, int limit) {
        List<RegionCode> codes = regionResolver.resolve(region);
        YearMonth latest = YearMonth.now(clock);
        // 공급자 제공 지연을 감안해 두 달 전부터 한 달씩 거슬러 조회합니다.
        for (int lag = 2; lag <= 4 && dataLab.configured(); lag++) {
            String baseYm = latest.minusMonths(lag).format(YM);
            List<JsonNode> hubs = hubItems(codes, baseYm);
            if (!hubs.isEmpty()) {
                List<RankingItemResponse> items = new ArrayList<>();
                for (JsonNode hub : hubs.stream().limit(limit).toList()) {
                    items.add(hubItem(items.size() + 1, hub, codes.getFirst().areaCode()));
                }
                return new RankingResponse(RankingType.ATTRACTION, region,
                        latest.minusMonths(lag).toString(), "KTO_DATALAB", true, items);
            }
        }
        return new RankingResponse(RankingType.ATTRACTION, region, "", "TOUR_API", false,
                tourListFallback(codes, limit));
    }

    private List<JsonNode> hubItems(List<RegionCode> codes, String baseYm) {
        List<JsonNode> all = new ArrayList<>();
        for (RegionCode code : codes) {
            if (!code.districtCode().isBlank()) {
                all.addAll(dataLab.hubAttractions(baseYm, code.areaCode(), code.areaCode() + code.districtCode()));
                continue;
            }
            List<JsonNode> areaWide = dataLab.hubAttractions(baseYm, code.areaCode(), null);
            if (!areaWide.isEmpty()) {
                all.addAll(areaWide);
                continue;
            }
            // 광역 단위 조회를 지원하지 않으면 시군구별 순위를 모아 같은 순위끼리 번갈아 배치합니다.
            for (String district : districtCodes(code.areaCode())) {
                List<JsonNode> districtItems = dataLab.hubAttractions(baseYm, code.areaCode(), district);
                if (districtItems.isEmpty() && all.isEmpty()) {
                    // 첫 시군구부터 비어 있으면 해당 월 자료가 아직 없는 것으로 보고 중단합니다.
                    break;
                }
                all.addAll(districtItems);
            }
        }
        Map<String, JsonNode> unique = new LinkedHashMap<>();
        all.stream()
                .filter(item -> !text(item, "hubTatsNm").isBlank())
                .filter(item -> EXCLUDED_HUB_CATEGORIES.stream()
                        .noneMatch(excluded -> text(item, "hubCtgryLclsNm").contains(excluded)))
                .sorted(Comparator.comparingInt(RankingService::hubRank))
                .forEach(item -> unique.putIfAbsent(normalizeName(text(item, "hubTatsNm")), item));
        return List.copyOf(unique.values());
    }

    private List<String> districtCodes(String areaCode) {
        try {
            return tourApi.legalDistricts(areaCode).stream()
                    .map(LegalDistrict::code)
                    .map(value -> value == null ? "" : value.replaceAll("[^0-9]", ""))
                    .map(digits -> digits.length() == 3 ? areaCode + digits : digits)
                    .filter(digits -> digits.length() == 5 && digits.startsWith(areaCode))
                    .distinct()
                    .limit(MAX_DISTRICT_CALLS)
                    .toList();
        } catch (RuntimeException exception) {
            log.warn("시군구 코드 조회 실패: {}", exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private RankingItemResponse hubItem(int rank, JsonNode hub, String areaCode) {
        String name = text(hub, "hubTatsNm");
        String category = firstNonBlank(text(hub, "hubCtgryMclsNm"), text(hub, "hubCtgryLclsNm"));
        String subtitle = joinNonBlank(" · ", joinNonBlank(" ", text(hub, "areaNm"), text(hub, "signguNm")), category);
        TourPlaceResponse matched = findTourPlace(name, areaCode);
        return new RankingItemResponse(
                rank,
                name,
                subtitle,
                matched == null ? null : image(matched),
                parseDouble(text(hub, "mapY")),
                parseDouble(text(hub, "mapX")),
                null,
                matched == null ? null : matched.contentId(),
                null,
                "방문 순위 " + text(hub, "hubRank") + "위");
    }

    /** 데이터랩 항목에는 이미지가 없어 같은 지역 TourAPI 키워드 검색으로 대표 이미지를 보강합니다. */
    private TourPlaceResponse findTourPlace(String name, String areaCode) {
        try {
            List<TourPlaceResponse> results = tourApi.searchKeyword(new KeywordSearchRequest(
                    5, null, "Q", name, areaCode, null, null, null, null)).items();
            String target = normalizeName(name);
            return results.stream()
                    .filter(place -> normalizeName(place.title()).contains(target)
                            || target.contains(normalizeName(place.title())))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private List<RankingItemResponse> tourListFallback(List<RegionCode> codes, int limit) {
        Map<String, TourPlaceResponse> unique = new LinkedHashMap<>();
        for (RegionCode code : codes) {
            try {
                tourApi.areaBasedList(new AreaBasedListRequest(
                                limit, null, "Q", "12", code.areaCode(), code.districtCode(), null, null, null))
                        .items()
                        .forEach(place -> unique.putIfAbsent(place.contentId(), place));
            } catch (RuntimeException exception) {
                log.warn("관광지 대체 목록 조회 실패: {}", exception.getClass().getSimpleName());
            }
        }
        List<RankingItemResponse> items = new ArrayList<>();
        for (TourPlaceResponse place : unique.values().stream().limit(limit).toList()) {
            items.add(tourItem(items.size() + 1, place, place.address(), null));
        }
        return items;
    }

    // ---- 축제: TourAPI 행사 정보 ----

    private RankingResponse festivals(String region, int limit) {
        LocalDate today = LocalDate.now(clock);
        List<RegionCode> codes = region.isEmpty() ? List.of() : regionResolver.resolve(region);
        Map<String, TourPlaceResponse> unique = new LinkedHashMap<>();
        List<RegionCode> targets = codes.isEmpty() ? java.util.Collections.singletonList(null) : codes;
        boolean providerFailed = false;
        for (RegionCode code : targets) {
            try {
                tourApi.searchFestival(new FestivalSearchRequest(
                                100, null, "C", today.format(YMD), null, null,
                                code == null ? null : code.areaCode(),
                                code == null || code.districtCode().isBlank() ? null : code.districtCode(),
                                null, null, null))
                        .items()
                        .forEach(place -> unique.putIfAbsent(place.contentId(), place));
            } catch (RuntimeException exception) {
                // 다른 순위와 같이 홈 화면을 오류로 막지 않고 대체(빈) 목록으로 응답합니다.
                log.warn("축제 순위 조회 실패: {}", exception.getClass().getSimpleName());
                providerFailed = true;
            }
        }
        // 축제는 공개 인기 지표가 없어 진행 중인 행사, 가까운 시작일, 이미지 보유 순으로 정렬합니다.
        List<TourPlaceResponse> sorted = unique.values().stream()
                .filter(place -> {
                    LocalDate end = parseYmd(place.eventEndDate());
                    return end == null || !end.isBefore(today);
                })
                .sorted(Comparator
                        .comparing((TourPlaceResponse place) -> {
                            LocalDate start = parseYmd(place.eventStartDate());
                            return start != null && !start.isAfter(today) ? 0 : 1;
                        })
                        .thenComparing(place -> Objects.requireNonNullElse(parseYmd(place.eventStartDate()), LocalDate.MAX))
                        .thenComparing(place -> image(place) == null ? 1 : 0))
                .limit(limit)
                .toList();
        List<RankingItemResponse> items = new ArrayList<>();
        for (TourPlaceResponse place : sorted) {
            LocalDate start = parseYmd(place.eventStartDate());
            LocalDate end = parseYmd(place.eventEndDate());
            boolean ongoing = start != null && !start.isAfter(today);
            String period = start == null ? "" : start.format(MONTH_DAY)
                    + (end == null || end.equals(start) ? "" : " ~ " + end.format(MONTH_DAY));
            items.add(tourItem(items.size() + 1, place, joinNonBlank(" · ", period, shortAddress(place.address())),
                    ongoing ? "진행 중" : "개최 예정"));
        }
        return new RankingResponse(RankingType.FESTIVAL, region, today.toString(), "TOUR_API",
                !(providerFailed && items.isEmpty()), items);
    }

    // ---- 인기 지역: 데이터랩 기초지자체 방문자 수 ----

    private RankingResponse regions(String region, int limit) {
        Set<String> areaCodes = new LinkedHashSet<>();
        Set<String> districtCodes = new LinkedHashSet<>();
        if (!region.isEmpty()) {
            for (RegionCode code : regionResolver.resolve(region)) {
                if (code.districtCode().isBlank()) {
                    areaCodes.add(code.areaCode());
                } else {
                    districtCodes.add(code.areaCode() + code.districtCode());
                }
            }
        }
        LocalDate end = LocalDate.now(clock).minusDays(7);
        // 방문자 수는 제공 지연이 있어 최근 7일 창을 한 달씩 뒤로 옮기며 자료가 있는 구간을 찾습니다.
        for (int attempt = 0; attempt < 3 && dataLab.configured(); attempt++) {
            LocalDate windowEnd = end.minusDays(30L * attempt);
            LocalDate windowStart = windowEnd.minusDays(6);
            List<JsonNode> rows = dataLab.districtVisitors(windowStart.format(YMD), windowEnd.format(YMD));
            List<RankingItemResponse> items = regionItems(rows, areaCodes, districtCodes, limit);
            if (!items.isEmpty()) {
                return new RankingResponse(RankingType.REGION, region,
                        windowStart + "~" + windowEnd, "KTO_DATALAB", true, items);
            }
        }
        return new RankingResponse(RankingType.REGION, region, "", "KTO_DATALAB", false, List.of());
    }

    private List<RankingItemResponse> regionItems(
            List<JsonNode> rows, Set<String> areaCodes, Set<String> districtCodes, int limit) {
        Map<String, RegionTotal> totals = new LinkedHashMap<>();
        for (JsonNode row : rows) {
            String code = text(row, "signguCode");
            // 현지인(1)을 제외한 외지인(2)·외국인(3) 방문을 인기 지표로 사용합니다.
            if (code.isBlank() || "1".equals(text(row, "touDivCd"))) {
                continue;
            }
            boolean filtered = !areaCodes.isEmpty() || !districtCodes.isEmpty();
            if (filtered && areaCodes.stream().noneMatch(code::startsWith)
                    && districtCodes.stream().noneMatch(code::startsWith)) {
                continue;
            }
            double visitors = parseDoubleOrZero(text(row, "touNum"));
            RegionTotal total = totals.computeIfAbsent(code,
                    ignored -> new RegionTotal(text(row, "signguNm"), text(row, "areaNm")));
            total.visitors += visitors;
            total.days.add(text(row, "baseYmd"));
        }
        List<RegionTotal> ranked = totals.values().stream()
                .filter(total -> total.visitors > 0)
                .sorted(Comparator.comparingDouble((RegionTotal total) -> total.visitors).reversed())
                .limit(limit)
                .toList();
        List<RankingItemResponse> items = new ArrayList<>();
        for (RegionTotal total : ranked) {
            long daily = Math.round(total.visitors / Math.max(1, total.days.size()));
            items.add(new RankingItemResponse(items.size() + 1, total.name, total.areaName, null, null, null,
                    null, null, daily, "일평균 방문 " + String.format(Locale.KOREA, "%,d", daily) + "명"));
        }
        return items;
    }

    // ---- 맛집: 가야디 찜 수 ----

    private RankingResponse restaurants(String region, int limit) {
        List<String> tokens = region.isEmpty() ? List.of() : Arrays.stream(region.split("[·\\s]+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .toList();
        List<RankingItemResponse> items = new ArrayList<>();
        for (FavoriteRankRow row : repository.findRestaurantsByFavorites(tokens, limit)) {
            items.add(new RankingItemResponse(items.size() + 1, row.name(), shortAddress(row.address()),
                    row.imageUrl(), row.latitude(), row.longitude(), row.placeId(), null,
                    row.favoriteCount(), "찜 " + row.favoriteCount() + "개"));
        }
        return new RankingResponse(RankingType.RESTAURANT, region, LocalDate.now(clock).toString(),
                "GAYADI_FAVORITES", true, items);
    }

    // ---- 공통 ----

    private static RankingItemResponse tourItem(int rank, TourPlaceResponse place, String subtitle, String metricLabel) {
        return new RankingItemResponse(rank, place.title(), subtitle, image(place),
                parseDouble(place.mapY()), parseDouble(place.mapX()), null, place.contentId(), null, metricLabel);
    }

    private static String image(TourPlaceResponse place) {
        String image = firstNonBlank(place.firstImage(), place.firstImage2());
        return image.isEmpty() ? null : image;
    }

    private static String shortAddress(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] parts = address.trim().split("\\s+");
        return String.join(" ", Arrays.copyOf(parts, Math.min(2, parts.length)));
    }

    private static int hubRank(JsonNode item) {
        try {
            return Integer.parseInt(text(item, "hubRank"));
        } catch (NumberFormatException exception) {
            return Integer.MAX_VALUE;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString("").trim();
    }

    private static String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("[\\s()·]", "").toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private static String joinNonBlank(String separator, String... values) {
        return String.join(separator, Arrays.stream(values)
                .filter(value -> value != null && !value.isBlank())
                .toList());
    }

    private static Double parseDouble(String value) {
        try {
            double parsed = Double.parseDouble(value);
            return Double.isFinite(parsed) && parsed != 0 ? parsed : null;
        } catch (NumberFormatException | NullPointerException exception) {
            return null;
        }
    }

    private static double parseDoubleOrZero(String value) {
        Double parsed = parseDouble(value);
        return parsed == null ? 0 : parsed;
    }

    private static LocalDate parseYmd(String value) {
        if (value == null || !value.matches("\\d{8}")) {
            return null;
        }
        return LocalDate.parse(value, YMD);
    }

    private void trimCache(Instant now) {
        cache.entrySet().removeIf(entry -> !entry.getValue().isFresh(now));
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }

    private record CacheKey(RankingType type, String region, int limit) {
    }

    private record Cached(RankingResponse response, Instant expiresAt) {
        boolean isFresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    private static final class RegionTotal {
        private final String name;
        private final String areaName;
        private final Set<String> days = new LinkedHashSet<>();
        private double visitors;

        private RegionTotal(String name, String areaName) {
            this.name = name;
            this.areaName = areaName;
        }
    }
}
