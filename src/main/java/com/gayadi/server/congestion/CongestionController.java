package com.gayadi.server.congestion;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastDetailResponse;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import com.gayadi.server.congestion.dto.response.CongestionHourlyForecastResponse;
import com.gayadi.server.congestion.dto.response.PlaceCongestionDetailResponse;
import com.gayadi.server.weather.CurrentWeatherSummaryService;
import com.gayadi.server.weather.WeatherErrorCode;
import com.gayadi.server.weather.dto.response.PlaceWeatherSummaryResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

import java.util.concurrent.CompletableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관광지의 실시간·예측 혼잡도 조회 HTTP 요청을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/congestion")
@SecurityRequirement(name = "bearerAuth")
public class CongestionController {

    private final CongestionForecastService service;
    private final PlaceCongestionService placeCongestion;
    private final CurrentWeatherSummaryService weather;

    public CongestionController(
            CongestionForecastService service,
            PlaceCongestionService placeCongestion,
            CurrentWeatherSummaryService weather) {
        this.service = service;
        this.placeCongestion = placeCongestion;
        this.weather = weather;
    }

    @GetMapping("/forecast")
    @Tag(name = "혼잡")
    @Operation(summary = "지역 혼잡·현재 날씨",
            description = "지역 코드의 일별 혼잡과 시간대 points입니다. "
                    + "hours를 생략하면 9, 11, 13, 15, 17, 19입니다. "
                    + "lat와 lon을 함께 보내면 초단기실황과 초단기예보로 만든 현재 날씨 요약이 포함됩니다. "
                    + "둘 다 실패하면 weather.available은 false입니다. "
                    + "저장 장소 번호는 GET /api/v1/congestion/places/{placeId}입니다.")
    @ApiResponse(responseCode = "200", description = "일별 혼잡 예상, 시간대 분포, 선택적 현재 날씨",
            content = @Content(schema = @Schema(implementation = CongestionForecastDetailResponse.class)))
    public CongestionForecastDetailResponse forecast(
            @Parameter(description = "시도 코드 2자리", example = "11", required = true)
            @RequestParam @Pattern(regexp = "\\d{2}") String areaCode,
            @Parameter(description = "시군구 코드 3자리 또는 5자리", example = "110", required = true)
            @RequestParam @Pattern(regexp = "\\d{3}|\\d{5}") String districtCode,
            @Parameter(description = "지역 표시명")
            @RequestParam(defaultValue = "") @Size(max = 50) String areaName,
            @Parameter(description = "장소 표시명")
            @RequestParam(defaultValue = "") @Size(max = 100) String placeName,
            @Parameter(description = "UTC 오프셋을 포함한 ISO-8601 예측 기준 시각")
            @RequestParam(defaultValue = "") @Size(max = 40) String targetAt,
            @Parameter(description = "조회할 시간(0-23). 생략하면 9, 11, 13, 15, 17, 19",
                    example = "9,11,13,15,17,19")
            @RequestParam(required = false) @Size(max = 24) java.util.List<@Min(0) @Max(23) Integer> hours,
            @Parameter(description = "현재 날씨를 포함할 위도. lon과 함께 보냅니다.", example = "37.5796")
            @RequestParam(required = false) @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @Parameter(description = "현재 날씨를 포함할 경도. lat와 함께 보냅니다.", example = "126.9770")
            @RequestParam(required = false) @DecimalMin("-180.0") @DecimalMax("180.0") Double lon) {
        return detail(areaCode, districtCode, areaName, placeName, targetAt, hours, lat, lon);
    }

    /** 이미 배포된 앱 버전 호환용입니다. 새 화면은 GET /forecast의 points를 사용합니다. */
    @Deprecated
    @GetMapping("/forecast/hourly")
    @Tag(name = "혼잡")
    @Operation(summary = "지역 시간대별 혼잡 (호환용)", deprecated = true,
            description = "이전 앱 버전 호환용입니다. GET /api/v1/congestion/forecast가 같은 points를 함께 반환합니다. "
                    + "시간대 값은 일별 점수에 분포를 적용한 추정치이며 신뢰도는 LOW입니다. JWT가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "시간대별 혼잡 예상값",
            content = @Content(schema = @Schema(implementation = CongestionHourlyForecastResponse.class)))
    public CongestionHourlyForecastResponse forecastHourly(
            @Parameter(description = "시도 코드 2자리", example = "11", required = true)
            @RequestParam @Pattern(regexp = "\\d{2}") String areaCode,
            @Parameter(description = "시군구 코드 3자리 또는 5자리", example = "110", required = true)
            @RequestParam @Pattern(regexp = "\\d{3}|\\d{5}") String districtCode,
            @Parameter(description = "지역 표시명")
            @RequestParam(defaultValue = "") @Size(max = 50) String areaName,
            @Parameter(description = "장소 표시명")
            @RequestParam(defaultValue = "") @Size(max = 100) String placeName,
            @Parameter(description = "UTC 오프셋을 포함한 ISO-8601 예측 기준 시각")
            @RequestParam(defaultValue = "") @Size(max = 40) String targetAt,
            @Parameter(description = "조회할 시간(0-23). 생략하면 9, 11, 13, 15, 17, 19",
                    example = "9,11,13,15,17,19")
            @RequestParam(required = false) java.util.List<@Min(0) @Max(23) Integer> hours) {
        return service.forecastHourly(new CongestionForecastRequest(
                areaCode, districtCode, areaName, placeName, targetAt), hours);
    }

    private CongestionForecastDetailResponse detail(
            String areaCode,
            String districtCode,
            String areaName,
            String placeName,
            String targetAt,
            java.util.List<Integer> hours,
            Double lat,
            Double lon) {
        if ((lat == null) != (lon == null)) {
            throw new BusinessException(WeatherErrorCode.WEATHER_LOCATION_PAIR_REQUIRED);
        }
        CompletableFuture<PlaceWeatherSummaryResponse> weatherFuture = lat == null
                ? CompletableFuture.completedFuture(null)
                : weather.summarizeAsync(lat, lon);
        CongestionForecastRequest request = new CongestionForecastRequest(
                areaCode, districtCode, areaName, placeName, targetAt);
        CongestionForecastResponse daily = service.forecast(request);
        CongestionHourlyForecastResponse hourly = service.hourlyFrom(daily, hours);
        return CongestionForecastDetailResponse.of(daily, hourly, weatherFuture.join());
    }

    /** 가야디에 저장된 장소의 상세·날씨·혼잡도를 조회합니다. */
    @GetMapping("/places/{placeId}")
    @Tag(name = "저장 장소")
    @Operation(summary = "저장 장소 혼잡",
            description = "저장된 가야디 장소 번호의 혼잡과 현재 날씨입니다. "
                    + "관광 contentId는 사용할 수 없습니다. "
                    + "관광 목록의 장소는 GET /api/v1/congestion/forecast에 지역 코드와 좌표를 보냅니다.")
    @ApiResponse(responseCode = "200", description = "장소별 혼잡도 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = PlaceCongestionDetailResponse.class)))
    public PlaceCongestionDetailResponse place(
            @PathVariable @Min(1) long placeId,
            @Parameter(description = "표시할 시간대 목록. 생략하면 9, 11, 13, 15, 17, 19시")
            @RequestParam(required = false) @Size(max = 24) java.util.List<@Min(0) @Max(23) Integer> hours) {
        return placeCongestion.get(placeId, hours);
    }
}
