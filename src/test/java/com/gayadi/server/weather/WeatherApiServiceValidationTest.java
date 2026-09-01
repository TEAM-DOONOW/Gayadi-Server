package com.gayadi.server.weather;

import com.gayadi.server.weather.dto.request.ForecastVersionRequest;
import com.gayadi.server.weather.dto.request.WeatherRequest;

import com.gayadi.server.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeatherApiServiceValidationTest {

    private final WeatherApiService service = new WeatherApiService(
            new ObjectMapper(), "", "https://example.invalid/weather");

    @Test
    void requiresOneCompleteLocationPairWithinTheSupportedGrid() {
        assertThatThrownBy(() -> service.ultraSrtNcst(request(37.56, null, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_LOCATION_PAIR_REQUIRED));
        assertThatThrownBy(() -> service.ultraSrtNcst(request(37.56, 126.98, 60, 127)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_LOCATION_TYPE_CONFLICT));
        assertThatThrownBy(() -> service.ultraSrtNcst(request(null, null, 0, 127)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_GRID_OUT_OF_RANGE));
        assertThatThrownBy(() -> service.ultraSrtNcst(request(0.0, 0.0, null, null)))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_LOCATION_UNSUPPORTED));
    }

    @Test
    void validatesForecastVersionInputsBeforeCallingTheProvider() {
        assertThatThrownBy(() -> service.fcstVersion(
                new ForecastVersionRequest("OTHER", "202608250200")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_VERSION_FILE_TYPE_INVALID));
        assertThatThrownBy(() -> service.fcstVersion(
                new ForecastVersionRequest("SHRT", "202602300200")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(WeatherErrorCode.WEATHER_VERSION_DATETIME_INVALID));
    }

    private WeatherRequest request(
            Double lat, Double lon, Integer nx, Integer ny) {
        return new WeatherRequest(lat, lon, nx, ny, null, null);
    }
}
