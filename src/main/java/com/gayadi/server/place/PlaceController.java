package com.gayadi.server.place;

import com.gayadi.server.place.dto.response.PlacePageResponse;
import com.gayadi.server.place.dto.response.PlaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 여행 장소 관련 HTTP 요청과 응답을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/places")
@Tag(name = "장소")
public class PlaceController {

    private final PlaceService service;

    public PlaceController(PlaceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "공개 장소 목록", description = "검색어와 지역, 분류로 공개 장소를 찾습니다. 식별자 기준으로 다음 페이지를 이어서 조회할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "조건에 맞는 공개 장소 목록입니다.",
            content = @Content(schema = @Schema(implementation = PlacePageResponse.class)))
    public PlacePageResponse list(
            @Parameter(description = "장소명, 주소 또는 기본 정보 검색어")
            @RequestParam(required = false) @Size(max = 100) String query,
            @Parameter(description = "지역명 또는 지역 식별자")
            @RequestParam(required = false) @Size(max = 50) String region,
            @Parameter(description = "장소 분류")
            @RequestParam(required = false) String category,
            @Parameter(description = "이전 응답의 다음 기준값")
            @RequestParam(required = false) @Min(1) Long cursor,
            @Parameter(description = "한 번에 받을 장소 수")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
        return service.list(query, region, category, cursor, limit);
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "공개 장소 상세")
    @ApiResponse(responseCode = "200", description = "공개 장소의 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = PlaceResponse.class)))
    public PlaceResponse get(@PathVariable @Min(1) long placeId) {
        return service.get(placeId);
    }
}
