package com.gayadi.server.ranking;

import com.gayadi.server.ranking.dto.response.RankingResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 홈 화면 카테고리별 TOP 순위 HTTP 요청을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/rankings")
@Tag(name = "순위", description = "관광지·축제·인기 지역·맛집 카테고리별 TOP 목록")
@SecurityRequirement(name = "bearerAuth")
public class RankingController {

    private final RankingService service;

    public RankingController(RankingService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "카테고리별 TOP 목록",
            description = "JWT가 필요합니다. ATTRACTION은 한국관광 데이터랩 중심 관광지 방문 순위(지역 미지정 시 서울), "
                    + "FESTIVAL은 진행 중·임박 축제, REGION은 데이터랩 기초지자체 외지인·외국인 방문자 수, "
                    + "RESTAURANT는 가야디 사용자 찜 수 순위입니다. 데이터랩 자료가 없으면 "
                    + "providerDataAvailable=false로 대체 목록(또는 빈 목록)을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "순위 목록",
            content = @Content(schema = @Schema(implementation = RankingResponse.class)))
    public RankingResponse rankings(
            @Parameter(description = "순위 종류", example = "ATTRACTION", required = true)
            @RequestParam RankingType type,
            @Parameter(description = "앱 여행 지역 이름. 비우면 전국", example = "서울")
            @RequestParam(defaultValue = "") @Size(max = 50) String region,
            @Parameter(description = "최대 항목 수", example = "10")
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return service.rank(type, region, limit);
    }
}
