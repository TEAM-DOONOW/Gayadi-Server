package com.gayadi.server.weather;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "날씨 API", description = "기상청 단기예보 조회서비스(VilageFcstInfoService_2.0) 연동")
public class WeatherApiController {

    private final WeatherApiService service;

    public WeatherApiController(WeatherApiService service) {
        this.service = service;
    }

    @GetMapping("/now")
    @Operation(summary = "초단기실황 조회",
            description = "현재 날씨 관측값을 조회한다. lat/lon(위경도) 또는 nx/ny(격자좌표) 중 하나를 지정한다. "
                    + "baseDate/baseTime을 생략하면 발표 가능한 최신 시각으로 자동 계산한다. "
                    + "항목: 기온(T1H), 1시간 강수량(RN1), 동서바람(UUU), 남북바람(VVV), "
                    + "습도(REH), 강수형태(PTY), 풍향(VEC), 풍속(WSD).")
    public WeatherApiService.UltraSrtNcstResponse now(
            @Parameter(description = "위도(WGS84)", example = "37.563569")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "경도(WGS84)", example = "126.980008")
            @RequestParam(required = false) Double lon,
            @Parameter(description = "예보지점 X 좌표", example = "60")
            @RequestParam(required = false) Integer nx,
            @Parameter(description = "예보지점 Y 좌표", example = "127")
            @RequestParam(required = false) Integer ny,
            @Parameter(description = "발표일자(YYYYMMDD). 생략 시 자동 계산")
            @RequestParam(required = false) String baseDate,
            @Parameter(description = "발표시각(HHMM). 생략 시 자동 계산")
            @RequestParam(required = false) String baseTime) {
        return service.ultraSrtNcst(new WeatherApiService.WeatherRequest(
                lat, lon, nx, ny, baseDate, baseTime));
    }

    @GetMapping("/ultra-forecast")
    @Operation(summary = "초단기예보 조회",
            description = "예보시점부터 6시간 이내의 예보를 조회한다. lat/lon 또는 nx/ny 중 하나를 지정한다. "
                    + "baseDate/baseTime을 생략하면 발표 가능한 최신 시각으로 자동 계산한다(매시각 30분 발표, 45분 이후 호출). "
                    + "항목: 기온(T1H), 1시간 강수량(RN1), 하늘상태(SKY), 동서바람(UUU), 남북바람(VVV), "
                    + "습도(REH), 강수형태(PTY), 강수확률(POP), 낙뢰(LGT), 풍향(VEC), 풍속(WSD).")
    public WeatherApiService.ForecastResponse ultraForecast(
            @Parameter(description = "위도(WGS84)", example = "37.563569")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "경도(WGS84)", example = "126.980008")
            @RequestParam(required = false) Double lon,
            @Parameter(description = "예보지점 X 좌표", example = "60")
            @RequestParam(required = false) Integer nx,
            @Parameter(description = "예보지점 Y 좌표", example = "127")
            @RequestParam(required = false) Integer ny,
            @Parameter(description = "발표일자(YYYYMMDD). 생략 시 자동 계산")
            @RequestParam(required = false) String baseDate,
            @Parameter(description = "발표시각(HHMM). 생략 시 자동 계산")
            @RequestParam(required = false) String baseTime) {
        return service.ultraSrtFcst(new WeatherApiService.WeatherRequest(
                lat, lon, nx, ny, baseDate, baseTime));
    }

    @GetMapping("/forecast")
    @Operation(summary = "단기예보 조회",
            description = "3~5일 기간의 단기예보를 조회한다. lat/lon 또는 nx/ny 중 하나를 지정한다. "
                    + "baseDate/baseTime을 생략하면 발표 가능한 최신 시각으로 자동 계산한다(1일 8회: 02,05,08,11,14,17,20,23시). "
                    + "항목: 강수확률(POP), 강수형태(PTY), 1시간 강수량(PCP), 습도(REH), 신적설(SNO), "
                    + "하늘상태(SKY), 기온(TMP), 일최저기온(TMN), 일최고기온(TMX), 동서바람(UUU), "
                    + "남북바람(VVV), 파고(WAV), 풍향(VEC), 풍속(WSD). "
                    + "발표시각(02,05,08,11,14시)은 3일차부터 3시간 간격, (17,20,23시)은 4일차부터 3시간 간격으로 제공한다.")
    public WeatherApiService.ForecastResponse forecast(
            @Parameter(description = "위도(WGS84)", example = "37.563569")
            @RequestParam(required = false) Double lat,
            @Parameter(description = "경도(WGS84)", example = "126.980008")
            @RequestParam(required = false) Double lon,
            @Parameter(description = "예보지점 X 좌표", example = "60")
            @RequestParam(required = false) Integer nx,
            @Parameter(description = "예보지점 Y 좌표", example = "127")
            @RequestParam(required = false) Integer ny,
            @Parameter(description = "발표일자(YYYYMMDD). 생략 시 자동 계산")
            @RequestParam(required = false) String baseDate,
            @Parameter(description = "발표시각(HHMM). 생략 시 자동 계산")
            @RequestParam(required = false) String baseTime) {
        return service.vilageFcst(new WeatherApiService.WeatherRequest(
                lat, lon, nx, ny, baseDate, baseTime));
    }

    @GetMapping("/version")
    @Operation(summary = "예보버전 조회",
            description = "단기예보 각 오퍼레이션의 수정된 예보 버전을 조회한다. "
                    + "ftype: ODAM(초단기실황), VSRT(초단기예보), SHRT(단기예보). "
                    + "baseDateTime: YYYYMMDDHHMM 형식(예: 202608210200).")
    public WeatherApiService.FcstVersionResponse version(
            @Parameter(description = "파일구분", example = "SHRT", required = true)
            @RequestParam String ftype,
            @Parameter(description = "발표일시분(YYYYMMDDHHMM)", example = "202608210200", required = true)
            @RequestParam String baseDateTime) {
        return service.fcstVersion(new WeatherApiService.FcstVersionRequest(ftype, baseDateTime));
    }
}
