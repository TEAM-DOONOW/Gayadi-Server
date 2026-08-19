package com.gayadi.server.tourapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/tour")
@Tag(name = "관광 API", description = "한국관광공사 국문 관광정보 서비스(KorService2) 연동")
public class TourApiController {

    private final TourApiService service;

    public TourApiController(TourApiService service) {
        this.service = service;
    }

    @GetMapping("/areas")
    @Operation(summary = "지역기반 관광정보 조회",
            description = "법정동 시도/시군구 코드와 관광 타입으로 지역 기반 관광정보 목록을 조회합니다. "
                    + "커서 기반 페이지네이션: 최초 호출 시 cursor 없이, 이후 응답의 nextCursor를 그대로 "
                    + "다음 요청의 cursor로 전달하면 다음 페이지를 조회한다. nextCursor가 null이면 마지막 페이지. "
                    + "관광 타입(contentTypeId): 12 관광지, 14 문화시설, 15 축제공연행사, 25 여행코스, "
                    + "28 레포츠, 32 숙박, 38 쇼핑, 39 음식점. "
                    + "정렬(arrange): A 제목순, C 수정일순, D 생성일순.")
    public TourApiService.AreaBasedListResponse areas(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "정렬 구분", example = "C")
            @RequestParam(defaultValue = "C") String arrange,
            @Parameter(description = "관광 타입 ID", example = "12")
            @RequestParam(required = false) String contentTypeId,
            @Parameter(description = "법정동 시도 코드", example = "26")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드", example = "380")
            @RequestParam(required = false) String lDongSignguCd,
            @Parameter(description = "분류체계 대분류", example = "NA")
            @RequestParam(required = false) String lclsSystm1,
            @Parameter(description = "분류체계 중분류", example = "NA04")
            @RequestParam(required = false) String lclsSystm2,
            @Parameter(description = "분류체계 소분류", example = "NA040500")
            @RequestParam(required = false) String lclsSystm3) {
        return service.areaBasedList(new TourApiService.AreaBasedListRequest(
                pageSize, cursor, arrange, contentTypeId,
                lDongRegnCd, lDongSignguCd,
                lclsSystm1, lclsSystm2, lclsSystm3));
    }
}
