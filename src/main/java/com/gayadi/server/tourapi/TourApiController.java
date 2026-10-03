package com.gayadi.server.tourapi;

import com.gayadi.server.common.response.ApiErrorResponse;
import com.gayadi.server.tourapi.dto.request.FestivalSearchRequest;
import com.gayadi.server.tourapi.dto.request.KeywordSearchRequest;
import com.gayadi.server.tourapi.dto.request.LocationBasedListRequest;
import com.gayadi.server.tourapi.dto.request.StaySearchRequest;
import com.gayadi.server.tourapi.dto.request.TourDiscoveryRequest;
import com.gayadi.server.tourapi.dto.response.TourDiscoveryResponse;
import com.gayadi.server.tourapi.dto.response.TourListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 관광정보 목록·검색·통합 조회 HTTP 요청을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/tour")
public class TourApiController {

    private static final String ARRANGE = "A 제목순, C 수정일순, D 생성일순";
    private static final String ARRANGE_WITH_DISTANCE = ARRANGE + ", E 거리순";

    private final TourApiService service;
    private final TourDiscoveryService discovery;

    public TourApiController(TourApiService service, TourDiscoveryService discovery) {
        this.service = service;
        this.discovery = discovery;
    }

    @GetMapping("/areas")
    @Tag(name = "관광 목록")
    @Operation(summary = "지역 장소·혼잡 목록",
            description = "지역명으로 장소와 예상 혼잡을 반환합니다. 인증이 없습니다. "
                    + "시간대와 현재 날씨는 GET /api/v1/congestion/forecast입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "장소와 예상 혼잡",
                    content = @Content(schema = @Schema(
                            implementation = TourDiscoveryResponse.class))),
            @ApiResponse(responseCode = "400", description = "지원하지 않는 지역명",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "공공데이터 API 요청 한도 초과",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "공공데이터 제공기관 응답 오류",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "관광정보 요청 과부하 또는 외부 연동 불가",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public TourDiscoveryResponse areas(
            @Parameter(description = "한 페이지 결과 수. 최대 20")
            @RequestParam(defaultValue = "20") @Min(1) @Max(20) int pageSize,
            @Parameter(hidden = true)
            @RequestParam(required = false) String cursor,
            @Parameter(hidden = true)
            @RequestParam(defaultValue = "C") String arrange,
            @Parameter(description = "여행 지역명. 예: 서울, 수원·용인, 수원", example = "서울", required = true)
            @RequestParam String regionName,
            @Parameter(description = "혼잡 기준일", example = "2026-09-01")
            @RequestParam(required = false) LocalDate targetDate,
            @Parameter(description = "12 관광지, 14 문화시설, 15 행사, 25 여행코스, 28 레포츠, 32 숙박, 38 쇼핑, 39 음식점",
                    example = "12")
            @RequestParam(required = false) String contentTypeId,
            @Parameter(hidden = true)
            @RequestParam(required = false) String lDongRegnCd,
            @Parameter(hidden = true)
            @RequestParam(required = false) String lDongSignguCd,
            @Parameter(description = "분류체계 대분류", example = "NA")
            @RequestParam(required = false) String lclsSystm1,
            @Parameter(description = "분류체계 중분류", example = "NA04")
            @RequestParam(required = false) String lclsSystm2,
            @Parameter(description = "분류체계 소분류", example = "NA040500")
            @RequestParam(required = false) String lclsSystm3) {
        return discovery.discover(new TourDiscoveryRequest(pageSize, regionName, targetDate,
                contentTypeId, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/locations")
    @Tag(name = "관광 검색")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "좌표 주변 장소",
            description = "경도 mapX, 위도 mapY, 반경 radius(m, 최대 20000)로 주변을 조회합니다. JWT가 필요합니다. "
                    + "지역 목록은 GET /api/v1/tour/areas입니다.")
    @ApiResponse(responseCode = "200", description = "주변 관광정보 목록",
            content = @Content(schema = @Schema(implementation = TourListResponse.class)))
    public TourListResponse locations(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = ARRANGE_WITH_DISTANCE, example = "E",
                    schema = @Schema(allowableValues = {"A", "C", "D", "E"}))
            @RequestParam(defaultValue = "E") String arrange,
            @Parameter(description = "GPS X좌표(WGS84 경도)", example = "126.98375", required = true)
            @RequestParam String mapX,
            @Parameter(description = "GPS Y좌표(WGS84 위도)", example = "37.563446", required = true)
            @RequestParam String mapY,
            @Parameter(description = "거리 반경(m, 최대 20000)", example = "1000", required = true)
            @RequestParam String radius,
            @Parameter(description = "12 관광지, 14 문화시설, 15 행사, 25 여행코스, 28 레포츠, 32 숙박, 38 쇼핑, 39 음식점",
                    example = "39")
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
        return service.locationBasedList(new LocationBasedListRequest(
                pageSize, cursor, arrange, mapX, mapY, radius, contentTypeId, modifiedtime,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/keywords")
    @Tag(name = "관광 검색")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "키워드 장소 검색",
            description = "키워드로 장소를 검색합니다. JWT가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "키워드 관광정보 목록",
            content = @Content(schema = @Schema(implementation = TourListResponse.class)))
    public TourListResponse keywords(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = ARRANGE, example = "C",
                    schema = @Schema(allowableValues = {"A", "C", "D"}))
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
        return service.searchKeyword(new KeywordSearchRequest(
                pageSize, cursor, arrange, keyword,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/festivals")
    @Tag(name = "관광 검색")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "행사 조회",
            description = "시작일 eventStartDate(YYYYMMDD)로 행사만 조회합니다. JWT가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "행사정보 목록",
            content = @Content(schema = @Schema(implementation = TourListResponse.class)))
    public TourListResponse festivals(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = ARRANGE, example = "C",
                    schema = @Schema(allowableValues = {"A", "C", "D"}))
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
        return service.searchFestival(new FestivalSearchRequest(
                pageSize, cursor, arrange, eventStartDate, eventEndDate, modifiedtime,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }

    @GetMapping("/stays")
    @Tag(name = "관광 검색")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "숙박 조회",
            description = "숙박만 조회합니다. JWT가 필요합니다.")
    @ApiResponse(responseCode = "200", description = "숙박정보 목록",
            content = @Content(schema = @Schema(implementation = TourListResponse.class)))
    public TourListResponse stays(
            @Parameter(description = "한 페이지 결과 수")
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
            @Parameter(description = "이전 응답의 nextCursor. 미전달 시 첫 페이지")
            @RequestParam(required = false) String cursor,
            @Parameter(description = ARRANGE, example = "C",
                    schema = @Schema(allowableValues = {"A", "C", "D"}))
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
        return service.searchStay(new StaySearchRequest(
                pageSize, cursor, arrange, modifiedtime,
                lDongRegnCd, lDongSignguCd, lclsSystm1, lclsSystm2, lclsSystm3));
    }
}
