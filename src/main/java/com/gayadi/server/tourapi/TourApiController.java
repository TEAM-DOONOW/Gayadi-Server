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
    public TourApiService.TourListResponse areas(
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

    @GetMapping("/locations")
    @Operation(summary = "위치기반 관광정보 조회",
            description = "좌표(mapX/mapY)와 반경(radius, m, 최대 20000)으로 주변 관광정보 목록을 조회한다. "
                    + "정렬(arrange): A 제목순, C 수정일순, D 생성일순, E 거리순. "
                    + "응답 항목에 중심 좌표로부터 거리(dist, m)가 추가된다.")
    public TourApiService.TourListResponse locations(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "정렬 구분", example = "E")
            @RequestParam(defaultValue = "E") String arrange,
            @Parameter(description = "GPS X좌표(WGS84 경도)", example = "126.98375", required = true)
            @RequestParam String mapX,
            @Parameter(description = "GPS Y좌표(WGS84 위도)", example = "37.563446", required = true)
            @RequestParam String mapY,
            @Parameter(description = "거리 반경(m, 최대 20000)", example = "1000", required = true)
            @RequestParam String radius,
            @Parameter(description = "관광 타입 ID", example = "39")
            @RequestParam(required = false) String contentTypeId,
            @Parameter(description = "콘텐츠 수정일(YYYYMMDD)")
            @RequestParam(required = false) String modifiedtime,
            @Parameter(description = "법정동 시도 코드")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드")
            @RequestParam(required = false) String lDongSignguCd,
            @Parameter(description = "분류체계 대분류")
            @RequestParam(required = false) String lclsSystm1,
            @Parameter(description = "분류체계 중분류")
            @RequestParam(required = false) String lclsSystm2,
            @Parameter(description = "분류체계 소분류")
            @RequestParam(required = false) String lclsSystm3) {
        return service.locationBasedList(new TourApiService.LocationBasedListRequest(
                pageSize, cursor, arrange, mapX, mapY, radius, contentTypeId, modifiedtime,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/keywords")
    @Operation(summary = "키워드 검색 조회",
            description = "키워드로 관광정보를 검색한다. 키워드는 필수이며 한국어 인코딩은 서버가 처리한다. "
                    + "정렬(arrange): A 제목순, C 수정일순, D 생성일순.")
    public TourApiService.TourListResponse keywords(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "정렬 구분", example = "C")
            @RequestParam(defaultValue = "C") String arrange,
            @Parameter(description = "검색 키워드", example = "시장", required = true)
            @RequestParam String keyword,
            @Parameter(description = "법정동 시도 코드")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드")
            @RequestParam(required = false) String lDongSignguCd,
            @Parameter(description = "분류체계 대분류")
            @RequestParam(required = false) String lclsSystm1,
            @Parameter(description = "분류체계 중분류")
            @RequestParam(required = false) String lclsSystm2,
            @Parameter(description = "분류체계 소분류")
            @RequestParam(required = false) String lclsSystm3) {
        return service.searchKeyword(new TourApiService.SearchKeywordRequest(
                pageSize, cursor, arrange, keyword,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/festivals")
    @Operation(summary = "행사정보 조회",
            description = "행사/공연/축제 정보를 날짜로 조회한다. eventStartDate는 필수(YYYYMMDD). "
                    + "응답 항목에 행사 시작일(eventStartDate)/종료일(eventEndDate)/"
                    + "진행상태(progressType)/축제유형(festivalType)이 추가된다. "
                    + "정렬(arrange): A 제목순, C 수정일순, D 생성일순.")
    public TourApiService.TourListResponse festivals(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "정렬 구분", example = "C")
            @RequestParam(defaultValue = "C") String arrange,
            @Parameter(description = "행사 시작일(YYYYMMDD)", example = "20260101", required = true)
            @RequestParam String eventStartDate,
            @Parameter(description = "행사 종료일(YYYYMMDD)", example = "20261231")
            @RequestParam(required = false) String eventEndDate,
            @Parameter(description = "콘텐츠 수정일(YYYYMMDD)")
            @RequestParam(required = false) String modifiedtime,
            @Parameter(description = "법정동 시도 코드")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드")
            @RequestParam(required = false) String lDongSignguCd,
            @Parameter(description = "분류체계 대분류")
            @RequestParam(required = false) String lclsSystm1,
            @Parameter(description = "분류체계 중분류")
            @RequestParam(required = false) String lclsSystm2,
            @Parameter(description = "분류체계 소분류")
            @RequestParam(required = false) String lclsSystm3) {
        return service.searchFestival(new TourApiService.SearchFestivalRequest(
                pageSize, cursor, arrange, eventStartDate, eventEndDate, modifiedtime,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/stays")
    @Operation(summary = "숙박정보 조회",
            description = "숙박 정보 목록을 조회한다(관광 타입 32). "
                    + "정렬(arrange): A 제목순, C 수정일순, D 생성일순.")
    public TourApiService.TourListResponse stays(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "정렬 구분", example = "C")
            @RequestParam(defaultValue = "C") String arrange,
            @Parameter(description = "콘텐츠 수정일(YYYYMMDD)")
            @RequestParam(required = false) String modifiedtime,
            @Parameter(description = "법정동 시도 코드")
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(description = "법정동 시군구 코드")
            @RequestParam(required = false) String lDongSignguCd,
            @Parameter(description = "분류체계 대분류")
            @RequestParam(required = false) String lclsSystm1,
            @Parameter(description = "분류체계 중분류")
            @RequestParam(required = false) String lclsSystm2,
            @Parameter(description = "분류체계 소분류")
            @RequestParam(required = false) String lclsSystm3) {
        return service.searchStay(new TourApiService.SearchStayRequest(
                pageSize, cursor, arrange, modifiedtime,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }
}
