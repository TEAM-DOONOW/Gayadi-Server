package com.gayadi.server.tourapi;

import com.gayadi.server.common.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/tour")
@Tag(name = "관광 API", description = "한국관광공사 국문 관광정보 서비스(KorService2) 연동")
public class TourApiController {

    private final TourApiService service;
    private final TourDiscoveryService discovery;

    public TourApiController(TourApiService service, TourDiscoveryService discovery) {
        this.service = service;
        this.discovery = discovery;
    }

    @GetMapping("/discover")
    @Operation(summary = "GAYADI 앱용 지역 장소·혼잡 예측 통합 조회",
            description = "앱의 지역 이름을 법정동 코드로 변환하고 관광정보와 여행일 기준 예상 혼잡도를 함께 반환합니다.")
    public TourDiscoveryService.DiscoveryResponse discover(
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int pageSize,
            @RequestParam String regionName,
            @RequestParam(required = false) LocalDate targetDate,
            @RequestParam(required = false) String contentTypeId,
            @RequestParam(required = false) String lclsSystm1,
            @RequestParam(required = false) String lclsSystm2,
            @RequestParam(required = false) String lclsSystm3) {
        return discovery.discover(new TourDiscoveryService.Request(pageSize, regionName, targetDate,
                contentTypeId, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/areas")
    @Operation(summary = "Android 호환 지역 장소·혼잡도 통합 조회",
            description = "기존 Android /areas 호출 규격을 유지하면서 앱 지역명을 법정동 코드로 변환하고 "
                    + "관광정보와 여행일 기준 예상 혼잡도를 함께 반환합니다. nextCursor는 항상 null입니다. "
                    + "관광 타입(contentTypeId): 12 관광지, 14 문화시설, 15 축제공연행사, 25 여행코스, "
                    + "28 레포츠, 32 숙박, 38 쇼핑, 39 음식점.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "관광지와 혼잡도 조회 성공",
                    content = @Content(schema = @Schema(
                            implementation = TourDiscoveryService.DiscoveryResponse.class))),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 지역명 또는 잘못된 요청값",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "공공데이터 API 요청 한도 초과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "공공데이터 제공기관 응답 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "관광정보 요청 과부하 또는 외부 연동 불가",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TourDiscoveryService.DiscoveryResponse areas(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int pageSize,
            @Parameter(description = "이전 Android 호환용. 현재는 사용하지 않음")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "이전 Android 호환용. 현재는 사용하지 않음", example = "C")
            @RequestParam(defaultValue = "C") String arrange,
            @Parameter(description = "앱 여행 지역명. Android의 국내 여행 지역 선택 문자열을 그대로 사용하며 "
                    + "'제주 성산'도 '제주' 별칭으로 허용합니다. 전체 목록은 프론트엔드 API 명세를 참고하세요.",
                    example = "서울", required = true)
            @RequestParam String regionName,
            @Parameter(description = "혼잡도 예측 기준일", example = "2026-09-01")
            @RequestParam(required = false) LocalDate targetDate,
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
        return discovery.discover(new TourDiscoveryService.Request(pageSize, regionName, targetDate,
                contentTypeId, lclsSystm1, lclsSystm2, lclsSystm3));
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
