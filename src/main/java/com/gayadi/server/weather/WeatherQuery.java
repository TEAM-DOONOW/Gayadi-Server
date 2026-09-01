package com.gayadi.server.weather;

import com.gayadi.server.weather.dto.request.WeatherRequest;

import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/** 실황·초단기예보·단기예보가 공통으로 사용하는 쿼리 파라미터입니다. */
public class WeatherQuery {

    @Parameter(description = "위도(WGS84). nx/ny와 함께 사용할 수 없음", example = "37.563569")
    @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
    @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
    private Double lat;

    @Parameter(description = "경도(WGS84). nx/ny와 함께 사용할 수 없음", example = "126.980008")
    @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
    @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
    private Double lon;

    @Parameter(description = "기상청 격자 X. lat/lon과 함께 사용할 수 없음", example = "60")
    @Min(value = 1, message = "nx는 1 이상이어야 합니다.")
    @Max(value = 149, message = "nx는 149 이하여야 합니다.")
    private Integer nx;

    @Parameter(description = "기상청 격자 Y. lat/lon과 함께 사용할 수 없음", example = "127")
    @Min(value = 1, message = "ny는 1 이상이어야 합니다.")
    @Max(value = 253, message = "ny는 253 이하여야 합니다.")
    private Integer ny;

    @Parameter(description = "발표일자(YYYYMMDD). baseTime과 함께 생략하면 자동 계산", example = "20260825")
    @Pattern(regexp = "\\d{8}", message = "baseDate는 YYYYMMDD 형식이어야 합니다.")
    private String baseDate;

    @Parameter(description = "발표시각(HHMM). 허용 시각은 조회 종류별 설명 참조", example = "1400")
    @Pattern(regexp = "\\d{4}", message = "baseTime은 HHMM 형식이어야 합니다.")
    private String baseTime;

    public WeatherRequest toRequest() {
        return new WeatherRequest(lat, lon, nx, ny, baseDate, baseTime);
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLon() {
        return lon;
    }

    public void setLon(Double lon) {
        this.lon = lon;
    }

    public Integer getNx() {
        return nx;
    }

    public void setNx(Integer nx) {
        this.nx = nx;
    }

    public Integer getNy() {
        return ny;
    }

    public void setNy(Integer ny) {
        this.ny = ny;
    }

    public String getBaseDate() {
        return baseDate;
    }

    public void setBaseDate(String baseDate) {
        this.baseDate = baseDate;
    }

    public String getBaseTime() {
        return baseTime;
    }

    public void setBaseTime(String baseTime) {
        this.baseTime = baseTime;
    }
}
