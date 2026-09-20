package com.gayadi.server.tourapi;

import com.gayadi.server.congestion.CongestionForecastService;
import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.recommendation.PlaceSnapshotWriter;
import com.gayadi.server.recommendation.model.TourContentType;
import com.gayadi.server.recommendation.model.TourPlaceCandidate;
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
    private final PlaceSnapshotWriter snapshots;
    private final Map<PlaceQuery, CachedPlaces> placeCache = new ConcurrentHashMap<>();
    private final Map<PlaceQuery, Object> placeCacheLocks = new ConcurrentHashMap<>();
    private final Semaphore placeLoadBulkhead = new Semaphore(MAX_CONCURRENT_PLACE_LOADS);

    public TourDiscoveryService(TourApiService tourApi, TourRegionResolver regionResolver,
                                CongestionForecastService congestion,
                                PlaceSnapshotWriter snapshots) {
        this.tourApi = tourApi;
        this.regionResolver = regionResolver;
        this.congestion = congestion;
        this.snapshots = snapshots;
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
        Map<String, Long> localIds = snapshots.save(
                places.stream().map(this::toSnapshot).toList(), request.regionName());
        List<TourDiscoveryPlaceResponse> result = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            TourPlaceResponse place = places.get(index);
            result.add(TourDiscoveryPlaceResponse.of(
                    place, forecasts.get(index), localIds.get(place.contentId())));
        }
        return new TourDiscoveryResponse(
                result,
                result.size(),
                request.pageSize(),
                null,
                request.regionName(),
                targetDate);
    }

    private TourPlaceCandidate toSnapshot(TourPlaceResponse place) {
        TourContentType type = TourContentType.fromCode(place.contentTypeId());
        String category = type == null ? "ETC" : type.category();
        if ("39".equals(place.contentTypeId())
                && place.lclsSystm2() != null
                && place.lclsSystm2().startsWith("FD05")) {
            category = "CAFE";
        }
        return new TourPlaceCandidate(
                place.contentId(), place.title(), category, place.contentTypeId(),
                joinAddress(place.address(), place.addressDetail()),
                decimal(place.mapY()), decimal(place.mapX()),
                type == null ? null : type.indoor(), null,
                place.title() + " " + joinAddress(place.address(), place.addressDetail()),
                place.firstImage());
    }

    private String joinAddress(String address, String detail) {
        String first = address == null ? "" : address.trim();
        String second = detail == null ? "" : detail.trim();
        return second.isBlank() ? first : (first + " " + second).trim();
    }

    private Double decimal(String value) {
        try {
            return value == null || value.isBlank() ? null : Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
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
