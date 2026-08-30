package com.gayadi.server.weather;

import com.gayadi.server.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KmaBaseTimeCalculatorTest {

    @Test
    void resolvesPublicationBoundariesAcrossDayAndHourChanges() {
        assertThat(KmaBaseTimeCalculator.latest(
                KmaBaseTimeCalculator.ForecastType.ULTRA_NCST,
                LocalDateTime.of(2026, 8, 25, 0, 5)))
                .isEqualTo(new KmaBaseTimeCalculator.BaseDateTime("20260824", "2300"));

        assertThat(KmaBaseTimeCalculator.latest(
                KmaBaseTimeCalculator.ForecastType.ULTRA_FCST,
                LocalDateTime.of(2026, 8, 25, 14, 44)))
                .isEqualTo(new KmaBaseTimeCalculator.BaseDateTime("20260825", "1330"));
        assertThat(KmaBaseTimeCalculator.latest(
                KmaBaseTimeCalculator.ForecastType.ULTRA_FCST,
                LocalDateTime.of(2026, 8, 25, 14, 45)))
                .isEqualTo(new KmaBaseTimeCalculator.BaseDateTime("20260825", "1430"));

        assertThat(KmaBaseTimeCalculator.latest(
                KmaBaseTimeCalculator.ForecastType.VILAGE_FCST,
                LocalDateTime.of(2026, 8, 25, 2, 9)))
                .isEqualTo(new KmaBaseTimeCalculator.BaseDateTime("20260824", "2300"));
        assertThat(KmaBaseTimeCalculator.latest(
                KmaBaseTimeCalculator.ForecastType.VILAGE_FCST,
                LocalDateTime.of(2026, 8, 25, 2, 10)))
                .isEqualTo(new KmaBaseTimeCalculator.BaseDateTime("20260825", "0200"));
    }

    @Test
    void validatesExplicitDateTimeAsAPairAndByForecastType() {
        assertThatThrownBy(() -> KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.VILAGE_FCST, "20260825", null))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_BASE_PAIR_REQUIRED));
        assertThatThrownBy(() -> KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.VILAGE_FCST, "20260230", "0200"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_BASE_DATETIME_INVALID));
        assertThatThrownBy(() -> KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.ULTRA_FCST, "20260825", "1400"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_BASE_TIME_UNAVAILABLE));
        assertThatThrownBy(() -> KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.ULTRA_NCST, "20260825", "2400"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_BASE_DATETIME_INVALID));
    }
}
