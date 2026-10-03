package com.gayadi.server.congestion;

import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import com.gayadi.server.congestion.dto.response.CongestionHourlyForecastResponse;
import com.gayadi.server.congestion.dto.response.PlaceCongestionDetailResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관광지의 실시간·예측 혼잡도 조회 HTTP 요청을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/congestion")
@Tag(name = "혼잡", description = "관광지 집중률 예측과 명시적인 저신뢰도 대체 추정")
@SecurityRequirement(name = "bearerAuth")
public class CongestionController {

    private final CongestionForecastService service;
    private final PlaceCongestionService placeCongestion;

    public CongestionController(
            CongestionForecastService service,
            PlaceCongestionService placeCongestion) {
        this.service = service;
        this.placeCongestion = placeCongestion;
    }

    @GetMapping("/forecast")
    @Operation(summary = "관광지 혼잡 예상",
            description = "JWT가 필요합니다. 한국관광공사 향후 30일 집중률을 우선 사용하고, "
                    + "자료가 없으면 달력 추정임을 표시해 반환합니다. "
                    + "앱 장소 목록의 혼잡은 `GET /api/v1/tour/areas` 응답에 이미 포함됩니다.")
    @ApiResponse(responseCode = "200", description = "혼잡 예상값",
            content = @Content(schema = @Schema(implementation = CongestionForecastResponse.class)))
    public CongestionForecastResponse forecast(
            @Parameter(description = "시도 코드 2자리", example = "11", required = true)
            @RequestParam @Pattern(regexp = "\\d{2}") String areaCode,
            @Parameter(description = "시군구 코드 3자리 또는 5자리", example = "110", required = true)
            @RequestParam @Pattern(regexp = "\\d{3}|\\d{5}") String districtCode,
            @Parameter(description = "지역 표시명")
            @RequestParam(defaultValue = "") @Size(max = 50) String areaName,
            @Parameter(description = "장소 표시명")
            @RequestParam(defaultValue = "") @Size(max = 100) String placeName,
            @Parameter(description = "UTC 오프셋을 포함한 ISO-8601 예측 기준 시각")
            @RequestParam(defaultValue = "") @Size(max = 40) String targetAt) {
        return service.forecast(new CongestionForecastRequest(
                areaCode, districtCode, areaName, placeName, targetAt));
    }

    @GetMapping("/forecast/hourly")
    @Operation(summary = "관광지 시간대별 혼잡 예상",
            description = "JWT가 필요합니다. 단건 예측과 같은 일별 기준 점수를 사용하고, "
                    + "시간대 분포를 적용한 추정치 목록을 반환합니다. "
                    + "제공기관 자료가 일별 상대 집중률이므로 시간대별 값은 항상 추정치이며 "
                    + "신뢰도는 LOW로 표시됩니다. 기존 단건 예측과 에이전트 동작은 바뀌지 않습니다.")
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
            @Parameter(description = "조회할 시간(0-23). 콤마 또는 반복 전달. 미전달 시 9,11,13,15,17,19",
                    example = "9,11,13,15,17,19")
            @RequestParam(required = false) java.util.List<@Min(0) @Max(23) Integer> hours) {
        return service.forecastHourly(new CongestionForecastRequest(
                areaCode, districtCode, areaName, placeName, targetAt), hours);
    }

    /** 장소·현재 날씨·실시간 또는 예상 혼잡도를 화면 단위로 조회합니다. */
    @GetMapping("/places/{placeId}")
    @Operation(summary = "장소별 혼잡도 상세 조회",
            description = "장소 정보와 현재 날씨, 실시간 또는 예상 혼잡도, 시간대별 혼잡도를 반환합니다. "
                    + "서울 지정 핫스팟은 서울시 데이터, 그 외 TMAP 지원 장소는 TMAP 데이터를 우선 사용하고 "
                    + "연동할 수 없으면 전국 관광 혼잡도 예측으로 대체합니다.")
    @ApiResponse(responseCode = "200", description = "장소별 혼잡도 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = PlaceCongestionDetailResponse.class)))
    public PlaceCongestionDetailResponse place(
            @PathVariable @Min(1) long placeId,
            @Parameter(description = "표시할 시간대 목록. 생략하면 9, 11, 13, 15, 17, 19시")
            @RequestParam(required = false) @Size(max = 24) java.util.List<@Min(0) @Max(23) Integer> hours) {
        return placeCongestion.get(placeId, hours);
    }
}
