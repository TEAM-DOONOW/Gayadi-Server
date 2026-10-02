package com.gayadi.server.weather;

import com.gayadi.server.weather.dto.request.ForecastVersionRequest;
import com.gayadi.server.weather.dto.response.ForecastVersionResponse;
import com.gayadi.server.weather.dto.response.UltraShortNowcastResponse;
import com.gayadi.server.weather.dto.response.WeatherForecastResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 실황·예보·파일 버전 조회 HTTP 요청을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/weather")
@Tag(name = "기상청 원본", description = "기상청 실황·예보 원본. 장소 화면은 혼잡 forecast의 weather를 사용합니다.")
@SecurityRequirement(name = "bearerAuth")
public class WeatherApiController {

    private final WeatherApiService service;

    public WeatherApiController(WeatherApiService service) {
        this.service = service;
    }

    @GetMapping("/nowcasts")
    @Operation(summary = "초단기실황",
            description = "현재 관측값 원본입니다. lat/lon 또는 nx/ny 중 하나입니다. "
                    + "baseTime을 보내면 정시(HH00)이고, 생략하면 최신 발표를 씁니다. JWT가 필요합니다. "
                    + "장소 화면은 GET /api/v1/congestion/forecast의 weather를 사용합니다.")
    @ApiResponse(responseCode = "200", description = "현재 관측값",
            content = @Content(schema = @Schema(
                    implementation = UltraShortNowcastResponse.class)))
    public UltraShortNowcastResponse now(
            @Valid @ParameterObject WeatherQuery query) {
        return service.ultraSrtNcst(query.toRequest());
    }

    @GetMapping("/ultra-forecast")
    @Operation(summary = "초단기예보",
            description = "6시간 이내 예보 원본입니다. lat/lon 또는 nx/ny 중 하나입니다. "
                    + "baseTime은 매시 30분입니다. JWT가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "6시간 이내 초단기예보",
            content = @Content(schema = @Schema(
                    implementation = WeatherForecastResponse.class)))
    public WeatherForecastResponse ultraForecast(
            @Valid @ParameterObject WeatherQuery query) {
        return service.ultraSrtFcst(query.toRequest());
    }

    @GetMapping("/forecast")
    @Operation(summary = "단기예보",
            description = "3~5일 예보 원본입니다. lat/lon 또는 nx/ny 중 하나입니다. "
                    + "baseTime은 0200, 0500, 0800, 1100, 1400, 1700, 2000, 2300입니다. JWT가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "전체 단기예보 페이지를 합친 결과",
            content = @Content(schema = @Schema(
                    implementation = WeatherForecastResponse.class)))
    public WeatherForecastResponse forecast(
            @Valid @ParameterObject WeatherQuery query) {
        return service.vilageFcst(query.toRequest());
    }

    @GetMapping("/version")
    @Operation(summary = "예보 버전",
            description = "예보 파일 버전입니다. ftype은 ODAM, VSRT, SHRT입니다. JWT가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "예보 파일 버전",
            content = @Content(schema = @Schema(
                    implementation = ForecastVersionResponse.class)))
    public ForecastVersionResponse version(
            @Parameter(description = "파일구분", example = "SHRT", required = true)
            @Pattern(regexp = "ODAM|VSRT|SHRT", message = "ftype은 ODAM, VSRT, SHRT 중 하나여야 합니다.")
            @RequestParam String ftype,
            @Parameter(description = "발표일시분(YYYYMMDDHHMM)", example = "202608210200", required = true)
            @Pattern(regexp = "\\d{12}", message = "baseDateTime은 YYYYMMDDHHMM 형식이어야 합니다.")
            @RequestParam String baseDateTime) {
        return service.fcstVersion(new ForecastVersionRequest(ftype, baseDateTime));
    }
}
