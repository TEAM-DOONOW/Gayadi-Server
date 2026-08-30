package com.gayadi.server.tourapi;

import com.gayadi.server.congestion.CongestionForecast;
import com.gayadi.server.congestion.CongestionForecastService;
import com.gayadi.server.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class TourDiscoveryService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final Duration PLACE_CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_PLACE_CACHE_ENTRIES = 256;
    private static final int MAX_CONCURRENT_PLACE_LOADS = 4;
    private final TourApiService tourApi;
    private final TourRegionResolver regionResolver;
    private final CongestionForecastService congestion;
    private final Map<PlaceQuery, CachedPlaces> placeCache = new ConcurrentHashMap<>();
    private final Map<PlaceQuery, Object> placeCacheLocks = new ConcurrentHashMap<>();
    private final Semaphore placeLoadBulkhead = new Semaphore(MAX_CONCURRENT_PLACE_LOADS);

    public TourDiscoveryService(TourApiService tourApi, TourRegionResolver regionResolver,
                                CongestionForecastService congestion) {
        this.tourApi = tourApi;
        this.regionResolver = regionResolver;
        this.congestion = congestion;
    }

    public DiscoveryResponse discover(Request request) {
        LocalDate targetDate = request.targetDate() == null
                ? LocalDate.now(KOREA) : request.targetDate();
        List<TourApiService.TourPlace> places = cachedPlaces(request);
        String targetAt = targetDate + "T14:00:00+09:00";
        List<CongestionForecastService.Request> forecastRequests = places.stream()
                .map(place -> new CongestionForecastService.Request(
                        place.lDongRegnCd(), place.lDongSignguCd(), request.regionName(),
                        place.title(), targetAt))
                .toList();
        List<CongestionForecast> forecasts = congestion.forecastAll(forecastRequests);
        List<DiscoveryPlace> result = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            result.add(new DiscoveryPlace(places.get(index), forecasts.get(index)));
        }
        return new DiscoveryResponse(result, result.size(), request.pageSize(), null,
                request.regionName(), targetDate);
    }

    private List<TourApiService.TourPlace> cachedPlaces(Request request) {
        PlaceQuery key = PlaceQuery.from(request);
        CachedPlaces cached = placeCache.get(key);
        if (cached != null && cached.isFresh()) return cached.places();

        Object lock = placeCacheLocks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                cached = placeCache.get(key);
                if (cached != null && cached.isFresh()) return cached.places();
                if (!placeLoadBulkhead.tryAcquire()) {
                    throw new BusinessException(TourApiErrorCode.TOUR_REQUEST_BUSY);
                }
                try {
                    List<TourApiService.TourPlace> places = loadPlaces(request);
                    trimPlaceCache();
                    placeCache.put(key, new CachedPlaces(places, Instant.now()));
                    return places;
                } finally {
                    placeLoadBulkhead.release();
                }
            }
        } finally {
            placeCacheLocks.remove(key, lock);
        }
    }

    private List<TourApiService.TourPlace> loadPlaces(Request request) {
        List<TourRegionResolver.RegionCode> regions = regionResolver.resolve(request.regionName());
        LinkedHashMap<String, TourApiService.TourPlace> unique = new LinkedHashMap<>();
        int sizePerRegion = Math.max(1, request.pageSize() / regions.size());
        for (TourRegionResolver.RegionCode region : regions) {
            TourApiService.TourListResponse page = tourApi.areaBasedList(
                    new TourApiService.AreaBasedListRequest(
                            sizePerRegion, null, "C", request.contentTypeId(),
                            region.areaCode(), region.districtCode(), request.lclsSystm1(),
                            request.lclsSystm2(), request.lclsSystm3()));
            page.items().forEach(place -> unique.putIfAbsent(place.contentId(), place));
        }
        return unique.values().stream().limit(request.pageSize()).toList();
    }

    private void trimPlaceCache() {
        placeCache.entrySet().removeIf(entry -> !entry.getValue().isFresh());
        if (placeCache.size() < MAX_PLACE_CACHE_ENTRIES) return;
        placeCache.entrySet().stream()
                .min(Map.Entry.comparingByValue((left, right) -> left.createdAt().compareTo(right.createdAt())))
                .ifPresent(entry -> placeCache.remove(entry.getKey(), entry.getValue()));
    }

    public record Request(int pageSize, String regionName, LocalDate targetDate,
                          String contentTypeId, String lclsSystm1,
                          String lclsSystm2, String lclsSystm3) {
    }

    private record PlaceQuery(int pageSize, String regionName, String contentTypeId,
                              String lclsSystm1, String lclsSystm2, String lclsSystm3) {
        private static PlaceQuery from(Request request) {
            return new PlaceQuery(request.pageSize(), normalize(request.regionName()),
                    normalize(request.contentTypeId()), normalize(request.lclsSystm1()),
                    normalize(request.lclsSystm2()), normalize(request.lclsSystm3()));
        }

        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private record CachedPlaces(List<TourApiService.TourPlace> places, Instant createdAt) {
        private CachedPlaces {
            places = List.copyOf(places);
        }

        private boolean isFresh() {
            return createdAt.plus(PLACE_CACHE_TTL).isAfter(Instant.now());
        }
    }

    @Schema(description = "Android 장소 목록과 관광지 혼잡도 예측을 합친 응답")
    public record DiscoveryResponse(
            @Schema(description = "관광지 및 혼잡도 목록") List<DiscoveryPlace> items,
            @Schema(description = "현재 응답 항목 수", example = "2") int totalCount,
            @Schema(description = "요청한 페이지 크기", example = "20") int pageSize,
            @Schema(description = "호환용 커서. 현재 항상 null", nullable = true) String nextCursor,
            @Schema(description = "요청한 앱 지역명", example = "서울") String regionName,
            @Schema(description = "혼잡도 예측 기준일", example = "2026-09-01") LocalDate targetDate) {
    }

    @Schema(description = "관광정보와 관광지 집중률 기반 혼잡도 예측을 합친 장소")
    public record DiscoveryPlace(
            @Schema(description = "TourAPI 콘텐츠 ID", example = "126508") String contentId,
            @Schema(description = "관광 타입 ID", example = "12") String contentTypeId,
            @Schema(description = "장소명", example = "경복궁") String title,
            @Schema(description = "주소", example = "서울특별시 종로구 사직로 161") String address,
            @Schema(description = "상세 주소") String addressDetail,
            @Schema(description = "대표 이미지 URL") String firstImage,
            @Schema(description = "WGS84 경도", example = "126.976993") String mapX,
            @Schema(description = "WGS84 위도", example = "37.578822") String mapY,
            @Schema(description = "법정동 시도 코드", example = "11") String lDongRegnCd,
            @Schema(description = "법정동 시군구 코드", example = "110") String lDongSignguCd,
            @Schema(description = "분류체계 대분류") String lclsSystm1,
            @Schema(description = "분류체계 중분류") String lclsSystm2,
            @Schema(description = "분류체계 소분류") String lclsSystm3,
            @Schema(description = "프론트 표시용 혼잡 단계", allowableValues = {"RELAXED", "NORMAL", "CROWDED"}, example = "NORMAL") String crowdLevel,
            @Schema(description = "관광지 집중률 점수(0~100)", minimum = "0", maximum = "100", example = "54") int concentrationScore,
            @Schema(description = "혼잡도 산출 출처", example = "TOURISM_CONCENTRATION_API") String crowdSource,
            @Schema(description = "공공데이터가 없어 추정 모델을 사용했는지", example = "false") boolean crowdEstimated,
            @Schema(description = "관광지 집중률 API 원자료 사용 여부", example = "true") boolean crowdProviderDataAvailable,
            @Schema(description = "예측 신뢰도", allowableValues = {"HIGH", "MEDIUM", "LOW"}, example = "HIGH") String crowdConfidence,
            @Schema(description = "혼잡도 산출 설명") String crowdMessage,
            @Schema(description = "혼잡도 예측 기준일", example = "2026-09-01") LocalDate crowdTargetDate) {
        public DiscoveryPlace(TourApiService.TourPlace place, CongestionForecast forecast) {
            this(place.contentId(), place.contentTypeId(), place.title(), place.address(),
                    place.addressDetail(), place.firstImage(), place.mapX(), place.mapY(),
                    place.lDongRegnCd(), place.lDongSignguCd(), place.lclsSystm1(),
                    place.lclsSystm2(), place.lclsSystm3(), forecast.level(),
                    forecast.concentrationScore(), forecast.source(), forecast.estimated(),
                    forecast.providerDataAvailable(), forecast.confidence(), forecast.message(),
                    forecast.targetDate());
        }
    }
}
