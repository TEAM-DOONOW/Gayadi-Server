package com.gayadi.server.place;

import com.gayadi.server.place.dto.response.PlacePageResponse;
import com.gayadi.server.place.dto.request.PlaceSearchRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springdoc.core.annotations.ParameterObject;
import com.gayadi.server.place.dto.response.PlaceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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
    @Operation(summary = "공개 장소 목록", description = "검색어·지역·분류로 장소를 찾습니다. sort=TRAVEL_TIME과 이전 장소 좌표로 이동시간순 정렬합니다. 다음 장소 좌표도 보내면 추가 이동시간순입니다. 이동시간 조회는 로그인이 필요하며 가까운 후보 최대 20개 내에서 비교합니다.")
    @ApiResponse(responseCode = "200", description = "조건에 맞는 공개 장소 목록입니다.",
            content = @Content(schema = @Schema(implementation = PlacePageResponse.class)))
    public PlacePageResponse list(
            @Valid @ModelAttribute @ParameterObject PlaceSearchRequest request,
            @AuthenticationPrincipal Long userId) {
        return service.search(request, userId);
    }

    @GetMapping("/{placeId}")
    @Operation(summary = "공개 장소 상세")
    @ApiResponse(responseCode = "200", description = "공개 장소의 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = PlaceResponse.class)))
    public PlaceResponse get(@PathVariable @Min(1) long placeId) {
        return service.get(placeId);
    }
}
