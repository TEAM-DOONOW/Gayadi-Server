package com.gayadi.server.weather;

import com.gayadi.server.common.ApiException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherApiServiceValidationTest {

    private final WeatherApiService service = new WeatherApiService(
            new ObjectMapper(), "", "https://example.invalid/weather");

    @Test
    void requiresOneCompleteLocationPairWithinTheSupportedGrid() {
        assertThatThrownBy(() -> service.ultraSrtNcst(request(37.56, null, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("각 쌍");
        assertThatThrownBy(() -> service.ultraSrtNcst(request(37.56, 126.98, 60, 127)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("한 가지 위치 형식");
        assertThatThrownBy(() -> service.ultraSrtNcst(request(null, null, 0, 127)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("nx 1~149");
        assertThatThrownBy(() -> service.ultraSrtNcst(request(0.0, 0.0, null, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("국내 위치");
    }

    @Test
    void validatesForecastVersionInputsBeforeCallingTheProvider() {
        assertThatThrownBy(() -> service.fcstVersion(
                new WeatherApiService.FcstVersionRequest("OTHER", "202608250200")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("ODAM, VSRT, SHRT");
        assertThatThrownBy(() -> service.fcstVersion(
                new WeatherApiService.FcstVersionRequest("SHRT", "202602300200")))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("유효한 YYYYMMDDHHMM");
    }

    private WeatherApiService.WeatherRequest request(
            Double lat, Double lon, Integer nx, Integer ny) {
        return new WeatherApiService.WeatherRequest(lat, lon, nx, ny, null, null);
    }
}
