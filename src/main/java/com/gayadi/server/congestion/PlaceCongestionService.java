package com.gayadi.server.congestion;

import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionHourlyForecastResponse;
import com.gayadi.server.congestion.dto.response.PlaceCongestionDetailResponse;
import com.gayadi.server.congestion.dto.response.PlaceCongestionSummaryResponse;
import com.gayadi.server.congestion.dto.response.PlaceHourlyCongestionResponse;
import com.gayadi.server.congestion.model.SeoulCongestionSnapshot;
import com.gayadi.server.congestion.model.TmapCongestionSnapshot;
import com.gayadi.server.place.PlaceService;
import com.gayadi.server.place.dto.response.PlaceResponse;
import com.gayadi.server.tourapi.TourRegionResolver;
import com.gayadi.server.tourapi.TourRegionResolver.RegionCode;
import com.gayadi.server.weather.CurrentWeatherSummaryService;
import com.gayadi.server.weather.dto.response.PlaceWeatherSummaryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/** 장소 상세 화면에 필요한 장소·날씨·혼잡도 데이터를 조합합니다. */
@Service
public class PlaceCongestionService {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private static final Logger log = LoggerFactory.getLogger(PlaceCongestionService.class);

    private final PlaceService places;
    private final CurrentWeatherSummaryService currentWeather;
    private final CongestionForecastService congestionForecast;
    private final TmapCongestionService tmapCongestion;
    private final SeoulCongestionService seoulCongestion;
    private final TourRegionResolver regionResolver;

    public PlaceCongestionService(
            PlaceService places,
            CurrentWeatherSummaryService currentWeather,
            CongestionForecastService congestionForecast,
            TmapCongestionService tmapCongestion,
            SeoulCongestionService seoulCongestion,
            TourRegionResolver regionResolver) {
        this.places = places;
        this.currentWeather = currentWeather;
        this.congestionForecast = congestionForecast;
        this.regionResolver = regionResolver;
        this.tmapCongestion = tmapCongestion;
        this.seoulCongestion = seoulCongestion;
    }

    /** 장소 ID 하나로 상세 정보와 현재 날씨, 현재·시간대별 혼잡도를 반환합니다. */
    public PlaceCongestionDetailResponse get(long placeId, List<Integer> hours) {
        PlaceResponse place = places.get(placeId);
        OffsetDateTime now = OffsetDateTime.now(KOREA);

        PlaceWeatherSummaryResponse weatherSummary = weather(place);
        PlaceCongestionSummaryResponse congestionSummary = congestion(place, now, hours);

        return new PlaceCongestionDetailResponse(place, weatherSummary, congestionSummary);
    }

    private PlaceWeatherSummaryResponse weather(PlaceResponse place) {
        if (place.latitude() == null || place.longitude() == null) {
            return unavailableWeather("장소 좌표가 없어 날씨를 조회할 수 없습니다.");
        }
        return currentWeather.summarize(place.latitude(), place.longitude());
    }

    private PlaceCongestionSummaryResponse congestion(
            PlaceResponse place,
            OffsetDateTime now,
            List<Integer> hours) {
        Optional<SeoulCongestionSnapshot> seoul = seoulCongestion.find(
                place.name(), place.regionName(), place.address(), hours);
        if (seoul.isPresent()) {
            return seoulSummary(seoul.get(), place, now, hours);
        }

        Optional<TmapCongestionSnapshot> realtime = tmapCongestion.find(
                place.name(), place.latitude(), place.longitude(), now.getDayOfWeek(), hours);
        if (realtime.isPresent()) {
            return tmapSummary(realtime.get(), place, now, hours);
        }

        return fallbackSummary(fallback(place, now, hours));
    }

    private PlaceCongestionSummaryResponse tmapSummary(
            TmapCongestionSnapshot snapshot,
            PlaceResponse place,
            OffsetDateTime now,
            List<Integer> hours) {
        List<PlaceHourlyCongestionResponse> hourly;
        String hourlyDataType;
        String hourlySource;
        String message;

        if (snapshot.hourly().isEmpty()) {
            CongestionHourlyForecastResponse fallback = fallback(place, now, hours);
            hourly = fallback.points().stream()
                    .map(point -> new PlaceHourlyCongestionResponse(
                            point.hour(), point.concentrationScore(), null, null, null, point.level()))
                    .toList();
            hourlyDataType = "DAILY_FORECAST_WITH_HOURLY_DISTRIBUTION";
            hourlySource = fallback.source();
            message = "현재 혼잡도는 TMAP 실시간 집계이며, 시간대별 값은 "
                    + "한국관광공사 예측 또는 달력 기반 추정값입니다.";
        } else {
            double maxDensity = snapshot.hourly().stream()
                    .mapToDouble(TmapCongestionSnapshot.Hourly::densityPerSquareMeter)
                    .max()
                    .orElse(0);
            hourly = snapshot.hourly().stream()
                    .map(point -> new PlaceHourlyCongestionResponse(
                            point.hour(),
                            relativeScore(point.densityPerSquareMeter(), maxDensity),
                            point.densityPerSquareMeter(),
                            null,
                            null,
                            point.level()))
                    .toList();
            hourlyDataType = "RECENT_30_DAY_SAME_WEEKDAY_RELATIVE_INDEX";
            hourlySource = "TMAP_PUZZLE";
            message = "현재 값은 최근 한 시간의 실시간 집계입니다. 시간대별 점수는 최근 30일 "
                    + "동일 요일 평균 밀도를 선택 시간대 최댓값 기준 0~100으로 환산했습니다.";
        }

        return new PlaceCongestionSummaryResponse(
                true,
                snapshot.realtime().level(),
                null,
                snapshot.realtime().densityPerSquareMeter(),
                null,
                null,
                "REALTIME",
                "TMAP_PUZZLE",
                snapshot.realtime().observedAt(),
                LocalDate.now(KOREA),
                hourlyDataType,
                hourlySource,
                message,
                hourly);
    }

    private PlaceCongestionSummaryResponse seoulSummary(
            SeoulCongestionSnapshot snapshot,
            PlaceResponse place,
            OffsetDateTime now,
            List<Integer> hours) {
        List<PlaceHourlyCongestionResponse> hourly;
        String hourlyDataType;
        String hourlySource;
        String message;

        if (snapshot.hourly().isEmpty()) {
            CongestionHourlyForecastResponse fallback = fallback(place, now, hours);
            hourly = fallback.points().stream()
                    .map(point -> new PlaceHourlyCongestionResponse(
                            point.hour(), point.concentrationScore(), null, null, null, point.level()))
                    .toList();
            hourlyDataType = "DAILY_FORECAST_WITH_HOURLY_DISTRIBUTION";
            hourlySource = fallback.source();
            message = "현재 혼잡도는 서울시 실시간 인구이며, 시간대별 값은 "
                    + "한국관광공사 예측 또는 달력 기반 추정값입니다.";
        } else {
            double maxPopulation = snapshot.hourly().stream()
                    .mapToDouble(SeoulCongestionSnapshot.Hourly::averagePopulation)
                    .max()
                    .orElse(0);
            hourly = snapshot.hourly().stream()
                    .map(point -> new PlaceHourlyCongestionResponse(
                            point.hour(),
                            relativeScore(point.averagePopulation(), maxPopulation),
                            null,
                            point.populationMin(),
                            point.populationMax(),
                            point.level()))
                    .toList();
            hourlyDataType = "PROVIDER_POPULATION_FORECAST_RELATIVE_INDEX";
            hourlySource = "SEOUL_REALTIME_POPULATION";
            message = "현재 값과 시간대별 예상 인구는 서울시 지정 핫스팟 데이터입니다. "
                    + "시간대별 점수는 선택 시간대의 예상 평균 인구 최댓값을 기준으로 환산했습니다.";
        }

        return new PlaceCongestionSummaryResponse(
                true,
                snapshot.level(),
                null,
                null,
                snapshot.populationMin(),
                snapshot.populationMax(),
                "REALTIME",
                "SEOUL_REALTIME_POPULATION",
                snapshot.observedAt(),
                LocalDate.now(KOREA),
                hourlyDataType,
                hourlySource,
                message,
                hourly);
    }

    private PlaceCongestionSummaryResponse fallbackSummary(CongestionHourlyForecastResponse fallback) {
        List<PlaceHourlyCongestionResponse> hourly = fallback.points().stream()
                .map(point -> new PlaceHourlyCongestionResponse(
                        point.hour(), point.concentrationScore(), null, null, null, point.level()))
                .toList();
        return new PlaceCongestionSummaryResponse(
                true,
                fallback.baseLevel(),
                fallback.baseScore(),
                null,
                null,
                null,
                "FORECAST",
                fallback.source(),
                null,
                fallback.targetDate(),
                "DAILY_FORECAST_WITH_HOURLY_DISTRIBUTION",
                fallback.source(),
                fallback.message(),
                hourly);
    }

    private CongestionHourlyForecastResponse fallback(
            PlaceResponse place,
            OffsetDateTime now,
            List<Integer> hours) {
        // 저장 장소도 주소의 시군구 코드로 관광공사 집중률 예측을 먼저 씁니다. 못 찾으면 달력 추정입니다.
        RegionCode code = regionCode(place);
        return congestionForecast.forecastHourly(
                new CongestionForecastRequest(
                        code == null ? "" : code.areaCode(),
                        code == null ? "" : code.districtCode(),
                        place.regionName(), place.name(), now.toString()),
                hours);
    }

    private RegionCode regionCode(PlaceResponse place) {
        for (String address : java.util.Arrays.asList(place.roadAddress(), place.address())) {
            if (address == null || address.isBlank()) {
                continue;
            }
            try {
                Optional<RegionCode> code = regionResolver.resolveAddress(address);
                if (code.isPresent()) {
                    return code.get();
                }
            } catch (RuntimeException exception) {
                log.warn("장소 주소의 지역 코드를 찾지 못했습니다: placeId={} ({})",
                        place.id(), exception.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private Integer relativeScore(double density, double maxDensity) {
        return maxDensity <= 0 ? 0 : (int) Math.round(Math.max(0, Math.min(100, density / maxDensity * 100)));
    }

    private PlaceWeatherSummaryResponse unavailableWeather(String message) {
        return new PlaceWeatherSummaryResponse(
                false, null, null, null, null, null, "KMA", message);
    }
}
