package com.gayadi.server.congestion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/congestion")
@Tag(name = "혼잡", description = "관광지 집중률 예측과 명시적인 저신뢰도 대체 추정")
public class CongestionController {

    private final CongestionForecastService service;

    public CongestionController(CongestionForecastService service) {
        this.service = service;
    }

    @GetMapping("/forecast")
    @Operation(summary = "관광지 혼잡 예상",
            description = "한국관광공사 향후 30일 집중률을 우선 사용하고, 자료가 없으면 달력 추정임을 표시해 반환합니다.")
    public CongestionForecast forecast(
            @RequestParam @Pattern(regexp = "\\d{2}") String areaCode,
            @RequestParam @Pattern(regexp = "\\d{3}|\\d{5}") String districtCode,
            @RequestParam(defaultValue = "") @Size(max = 50) String areaName,
            @RequestParam(defaultValue = "") @Size(max = 100) String placeName,
            @RequestParam(defaultValue = "") @Size(max = 40) String targetAt) {
        return service.forecast(new CongestionForecastService.Request(
                areaCode, districtCode, areaName, placeName, targetAt));
    }
}
