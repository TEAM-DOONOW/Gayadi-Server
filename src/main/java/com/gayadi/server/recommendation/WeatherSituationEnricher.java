package com.gayadi.server.recommendation;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.weather.WeatherApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 사용자가 날씨를 생략한 실시간 상황 요청을 기상청 초단기실황으로 보강합니다. */
@Service
public class WeatherSituationEnricher {

    private static final Logger log = LoggerFactory.getLogger(WeatherSituationEnricher.class);

    private final WeatherApiService weather;

    public WeatherSituationEnricher(WeatherApiService weather) {
        this.weather = weather;
    }

    public TravelSituation enrich(TravelSituation situation, double latitude, double longitude) {
        TravelSituation current = situation == null ? TravelSituation.empty() : situation;
        if (!current.weather().isEmpty()) return current;

        try {
            WeatherApiService.UltraSrtNcstResponse observation = weather.ultraSrtNcst(
                    new WeatherApiService.WeatherRequest(
                            latitude, longitude, null, null, null, null));
            TravelSituation.Weather observed = new TravelSituation.Weather(
                    observation.precipitationTypeName(),
                    observation.precipitationTypeName(),
                    precipitation(observation.hourlyPrecipitationRaw()),
                    null,
                    decimal(observation.temperature()),
                    decimal(observation.windSpeed()));
            return new TravelSituation(observed, current.congestion(), current.transit());
        } catch (BusinessException exception) {
            // 추천 Agent는 기상청 장애와 독립적으로 사용 가능해야 한다.
            log.warn("기상청 실황 보강 생략: code={}", exception.getErrorCode().code());
            return current;
        }
    }

    private Double decimal(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Double precipitation(String value) {
        if (value == null || value.isBlank() || "강수없음".equals(value) || "0".equals(value)) {
            return 0.0;
        }
        return decimal(value.replace("mm", "").trim());
    }
}
