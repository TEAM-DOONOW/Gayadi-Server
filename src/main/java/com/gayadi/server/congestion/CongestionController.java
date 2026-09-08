package com.gayadi.server.congestion;

import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관광지 혼잡도 예측 조회 HTTP 요청을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/congestion")
@Tag(name = "혼잡", description = "관광지 집중률 예측과 명시적인 저신뢰도 대체 추정")
@SecurityRequirement(name = "bearerAuth")
public class CongestionController {

    private final CongestionForecastService service;

    public CongestionController(CongestionForecastService service) {
        this.service = service;
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
            @Parameter(description = "예측 기준 시각 또는 날짜")
            @RequestParam(defaultValue = "") @Size(max = 40) String targetAt) {
        return service.forecast(new CongestionForecastRequest(
                areaCode, districtCode, areaName, placeName, targetAt));
    }
}
