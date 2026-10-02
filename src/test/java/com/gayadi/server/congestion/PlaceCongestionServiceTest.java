package com.gayadi.server.congestion;

import com.gayadi.server.congestion.dto.response.CongestionHourlyForecastResponse;
import com.gayadi.server.congestion.dto.response.CongestionHourlyPoint;
import com.gayadi.server.congestion.dto.response.PlaceCongestionDetailResponse;
import com.gayadi.server.place.PlaceService;
import com.gayadi.server.place.dto.response.PlaceResponse;
import com.gayadi.server.weather.WeatherApiService;
import com.gayadi.server.weather.dto.response.UltraShortNowcastResponse;
import com.gayadi.server.weather.dto.response.WeatherForecastResponse;
import com.gayadi.server.weather.dto.response.WeatherForecastSlotResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlaceCongestionServiceTest {

    @Test
    void combinesCurrentObservationAndNearestForecastForPlaceWeather() {
        PlaceService places = mock(PlaceService.class);
        WeatherApiService weather = mock(WeatherApiService.class);
        CongestionForecastService congestionForecast = mock(CongestionForecastService.class);
        TmapCongestionService tmapCongestion = mock(TmapCongestionService.class);
        SeoulCongestionService seoulCongestion = mock(SeoulCongestionService.class);
        PlaceResponse place = mock(PlaceResponse.class);

        when(place.id()).thenReturn(1L);
        when(place.name()).thenReturn("경복궁");
        when(place.regionName()).thenReturn("서울");
        when(place.address()).thenReturn("서울 종로구 사직로 161");
        when(place.latitude()).thenReturn(37.5796);
        when(place.longitude()).thenReturn(126.977);
        when(places.get(1L)).thenReturn(place);

        when(weather.ultraSrtNcst(any())).thenReturn(nowcast());
        when(weather.vilageFcst(any())).thenReturn(forecast());
        when(seoulCongestion.find(anyString(), anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        when(tmapCongestion.find(anyString(), any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(congestionForecast.forecastHourly(any(), anyList())).thenReturn(congestion());

        PlaceCongestionService service = new PlaceCongestionService(
                places, weather, congestionForecast, tmapCongestion, seoulCongestion);

        PlaceCongestionDetailResponse result = service.get(1L, List.of(9, 11));

        assertThat(result.weather().available()).isTrue();
        assertThat(result.weather().condition()).isEqualTo("맑음");
        assertThat(result.weather().temperatureCelsius()).isEqualTo(23.0);
        assertThat(result.weather().precipitationProbability()).isEqualTo(10);
        assertThat(result.weather().source()).isEqualTo("KMA");
    }

    private UltraShortNowcastResponse nowcast() {
        return new UltraShortNowcastResponse(
                "20260924", "2200", 60, 127,
                "23.0", "0", "강수 없음", "0", "0", "55",
                "0", "없음", "180", "남풍", "1.2", "약한 바람");
    }

    private WeatherForecastResponse forecast() {
        WeatherForecastSlotResponse slot = new WeatherForecastSlotResponse(
                "20260924", "2300", "22.0", "강수 없음", "1", "맑음",
                "0", "0", "55", "0", "없음", "10", "0",
                "180", "남풍", "1.2", "약한 바람", "적설 없음", "", "", "");
        return new WeatherForecastResponse("20260924", "2000", 60, 127, List.of(slot));
    }

    private CongestionHourlyForecastResponse congestion() {
        return new CongestionHourlyForecastResponse(
                "서울", "경복궁", LocalDate.of(2026, 9, 24),
                "NORMAL", 50, "CALENDAR_HEURISTIC", true, false,
                "LOW", "추정값", List.of(
                new CongestionHourlyPoint(9, 45, "NORMAL"),
                new CongestionHourlyPoint(11, 55, "NORMAL")));
    }
}
