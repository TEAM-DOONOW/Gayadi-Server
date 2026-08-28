package com.gayadi.server.weather;

import com.gayadi.server.common.ApiException;
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
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("함께 입력");
        assertThatThrownBy(() -> KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.VILAGE_FCST, "20260230", "0200"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("YYYYMMDD");
        assertThatThrownBy(() -> KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.ULTRA_FCST, "20260825", "1400"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("사용할 수 없는 발표시각");
        assertThatThrownBy(() -> KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.ULTRA_NCST, "20260825", "2400"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("HHMM");
    }
}
