package com.gayadi.server.recommendation;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.weather.WeatherErrorCode;
import com.gayadi.server.weather.WeatherApiService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherSituationEnricherTest {

    @Test
    void appliesKmaObservationOnlyWhenWeatherWasNotProvided() {
        AtomicInteger calls = new AtomicInteger();
        WeatherApiService provider = new StubWeatherService(calls, false);
        WeatherSituationEnricher enricher = new WeatherSituationEnricher(provider);

        TravelSituation enriched = enricher.enrich(TravelSituation.empty(), 37.56, 126.98);

        assertThat(calls).hasValue(1);
        assertThat(enriched.weather().condition()).isEqualTo("비");
        assertThat(enriched.weather().temperatureC()).isEqualTo(23.5);
        assertThat(enriched.weather().windSpeedMps()).isEqualTo(3.2);
        assertThat(enriched.policy().indoorRequired()).isTrue();

        TravelSituation provided = new TravelSituation(
                new TravelSituation.Weather("CLEAR", "없음", 0.0, 0, 25.0, 1.0),
                TravelSituation.Congestion.empty(), TravelSituation.Transit.empty());
        assertThat(enricher.enrich(provided, 37.56, 126.98)).isSameAs(provided);
        assertThat(calls).hasValue(1);
    }

    @Test
    void keepsUserSituationWhenKmaIsUnavailable() {
        TravelSituation original = TravelSituation.empty();
        WeatherSituationEnricher enricher = new WeatherSituationEnricher(
                new StubWeatherService(new AtomicInteger(), true));

        assertThat(enricher.enrich(original, 37.56, 126.98)).isSameAs(original);
    }

    private static final class StubWeatherService extends WeatherApiService {
        private final AtomicInteger calls;
        private final boolean fail;

        private StubWeatherService(AtomicInteger calls, boolean fail) {
            super(new ObjectMapper(), "", "https://example.invalid/weather");
            this.calls = calls;
            this.fail = fail;
        }

        @Override
        public UltraSrtNcstResponse ultraSrtNcst(WeatherRequest request) {
            calls.incrementAndGet();
            if (fail) throw new BusinessException(WeatherErrorCode.WEATHER_API_FAILED);
            return new UltraSrtNcstResponse(
                    "20260825", "1400", 60, 127,
                    "23.5", "0.5", "1mm 미만", "1.0", "0.5", "85",
                    "1", "비", "180", "S", "3.2", "약한 바람");
        }
    }
}
