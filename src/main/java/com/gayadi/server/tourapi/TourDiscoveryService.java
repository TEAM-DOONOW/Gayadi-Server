package com.gayadi.server.tourapi;

import com.gayadi.server.congestion.CongestionForecastService;
import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.tourapi.dto.request.AreaBasedListRequest;
import com.gayadi.server.tourapi.dto.request.TourDiscoveryRequest;
import com.gayadi.server.tourapi.dto.response.TourDiscoveryPlaceResponse;
import com.gayadi.server.tourapi.dto.response.TourDiscoveryResponse;
import com.gayadi.server.tourapi.dto.response.TourListResponse;
import com.gayadi.server.tourapi.dto.response.TourPlaceResponse;
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

/** 지역 관광정보와 혼잡도 예측을 하나의 조회 결과로 조합합니다. */
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

    /** 관광지 정보와 혼잡도 예측을 조합해 탐색 결과를 반환합니다. */
    public TourDiscoveryResponse discover(TourDiscoveryRequest request) {
        LocalDate targetDate = request.targetDate() == null
                ? LocalDate.now(KOREA) : request.targetDate();
        List<TourPlaceResponse> places = cachedPlaces(request);
        String targetAt = targetDate + "T14:00:00+09:00";
        List<CongestionForecastRequest> forecastRequests = places.stream()
                .map(place -> new CongestionForecastRequest(
                        place.lDongRegnCd(), place.lDongSignguCd(), request.regionName(),
                        place.title(), targetAt))
                .toList();
        List<CongestionForecastResponse> forecasts = congestion.forecastAll(forecastRequests);
        List<TourDiscoveryPlaceResponse> result = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            result.add(TourDiscoveryPlaceResponse.of(places.get(index), forecasts.get(index)));
        }
        return new TourDiscoveryResponse(
                result,
                result.size(),
                request.pageSize(),
                null,
                request.regionName(),
                targetDate);
    }

    private List<TourPlaceResponse> cachedPlaces(TourDiscoveryRequest request) {
        PlaceQuery key = PlaceQuery.from(request);
        CachedPlaces cached = placeCache.get(key);
        if (cached != null && cached.isFresh()) {
            return cached.places();
        }

        Object lock = placeCacheLocks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                cached = placeCache.get(key);
                if (cached != null && cached.isFresh()) {
                    return cached.places();
                }
                if (!placeLoadBulkhead.tryAcquire()) {
                    throw new BusinessException(TourApiErrorCode.TOUR_REQUEST_BUSY);
                }
                try {
                    List<TourPlaceResponse> places = loadPlaces(request);
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

    private List<TourPlaceResponse> loadPlaces(TourDiscoveryRequest request) {
        List<TourRegionResolver.RegionCode> regions = regionResolver.resolve(request.regionName());
        LinkedHashMap<String, TourPlaceResponse> unique = new LinkedHashMap<>();
        int sizePerRegion = Math.max(1, request.pageSize() / regions.size());
        for (TourRegionResolver.RegionCode region : regions) {
            TourListResponse page = tourApi.areaBasedList(
                    new AreaBasedListRequest(
                            sizePerRegion, null, "C", request.contentTypeId(),
                            region.areaCode(), region.districtCode(), request.lclsSystm1(),
                            request.lclsSystm2(), request.lclsSystm3()));
            page.items().forEach(place -> unique.putIfAbsent(place.contentId(), place));
        }
        return unique.values().stream()
                .limit(request.pageSize())
                .toList();
    }

    private void trimPlaceCache() {
        placeCache.entrySet().removeIf(entry -> !entry.getValue().isFresh());
        if (placeCache.size() < MAX_PLACE_CACHE_ENTRIES) {
            return;
        }
        placeCache.entrySet().stream()
                .min(Map.Entry.comparingByValue((left, right) -> left.createdAt().compareTo(right.createdAt())))
                .ifPresent(entry -> placeCache.remove(entry.getKey(), entry.getValue()));
    }

    private record PlaceQuery(
            int pageSize,
            String regionName,
            String contentTypeId,
            String lclsSystm1,
            String lclsSystm2,
            String lclsSystm3
    ) {
        private static PlaceQuery from(TourDiscoveryRequest request) {
            return new PlaceQuery(
                    request.pageSize(),
                    normalize(request.regionName()),
                    normalize(request.contentTypeId()),
                    normalize(request.lclsSystm1()),
                    normalize(request.lclsSystm2()),
                    normalize(request.lclsSystm3()));
        }
        private static String normalize(String value) {
            return value == null ? "" : value.trim();
        }
    }

    private record CachedPlaces(
            List<TourPlaceResponse> places,
            Instant createdAt
    ) {
        private CachedPlaces {
            places = List.copyOf(places);
        }

        private boolean isFresh() {

            return createdAt.plus(PLACE_CACHE_TTL).isAfter(Instant.now());

        }
    }

}
