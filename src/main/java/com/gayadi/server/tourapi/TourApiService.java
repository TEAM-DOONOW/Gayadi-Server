package com.gayadi.server.tourapi;

import com.gayadi.server.tourapi.dto.request.AreaBasedListRequest;
import com.gayadi.server.tourapi.dto.request.FestivalSearchRequest;
import com.gayadi.server.tourapi.dto.request.KeywordSearchRequest;
import com.gayadi.server.tourapi.dto.request.LocationBasedListRequest;
import com.gayadi.server.tourapi.dto.request.StaySearchRequest;
import com.gayadi.server.tourapi.dto.response.TourListResponse;
import com.gayadi.server.tourapi.dto.response.TourPlaceDetailResponse;
import com.gayadi.server.tourapi.dto.response.TourPlaceResponse;
import com.gayadi.server.tourapi.model.LegalDistrict;

import com.gayadi.server.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 한국관광공사 API 호출 결과를 서비스 응답 DTO로 변환합니다. */
@Service
public class TourApiService {

    private static final Logger log = LoggerFactory.getLogger(TourApiService.class);

    private static final String OP_AREA_BASED_LIST = "areaBasedList2";
    private static final String OP_LOCATION_BASED_LIST = "locationBasedList2";
    private static final String OP_SEARCH_KEYWORD = "searchKeyword2";
    private static final String OP_SEARCH_FESTIVAL = "searchFestival2";
    private static final String OP_SEARCH_STAY = "searchStay2";
    private static final String OP_LEGAL_CODE = "ldongCode2";
    private static final String RESULT_CODE_OK = "0000";

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceKey;
    private final String mobileApp;

    public TourApiService(
            ObjectMapper objectMapper,
            @Value("${tour.api.key:}") String serviceKey,
            @Value("${tour.api.base-url:https://apis.data.go.kr/B551011/KorService2}") String baseUrl,
            @Value("${tour.api.mobile-app:Gayadi}") String mobileApp) {
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
        this.mobileApp = mobileApp;
    }

    /** 지역 및 시군구를 기반으로 관광정보 목록을 조회한다. */
    public TourListResponse areaBasedList(AreaBasedListRequest req) {
        int pageNo = TourCursor.decodePageNo(req.cursor());
        Map<String, String> params = baseParams(req.pageSize(), pageNo, req.arrange());
        putIfPresent(params, "contentTypeId", req.contentTypeId());
        putIfPresent(params, "lDongRegnCd", req.lDongRegnCd());
        putIfPresent(params, "lDongSignguCd", req.lDongSignguCd());
        putIfPresent(params, "lclsSystm1", req.lclsSystm1());
        putIfPresent(params, "lclsSystm2", req.lclsSystm2());
        putIfPresent(params, "lclsSystm3", req.lclsSystm3());
        return listResponse(OP_AREA_BASED_LIST, params, req.pageSize(), pageNo);
    }

    /** 법정동 시도 코드에 속한 시군구 코드를 조회한다. */
    public List<LegalDistrict> legalDistricts(String regionCode) {
        requireParam("lDongRegnCd", regionCode);
        Map<String, String> params = baseParams(100, 1, null);
        params.put("lDongRegnCd", regionCode);
        JsonNode itemNode = call(OP_LEGAL_CODE, params).path("body").path("items").path("item");
        List<LegalDistrict> result = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) result.add(toLegalDistrict(node));
        } else if (itemNode.isObject()) {
            result.add(toLegalDistrict(itemNode));
        }
        return List.copyOf(result);
    }

    /** 도메인 관련 도메인 업무를 처리합니다. */
    public TourListResponse locationBasedList(LocationBasedListRequest req) {
        int pageNo = TourCursor.decodePageNo(req.cursor());
        Map<String, String> params = baseParams(req.pageSize(), pageNo, req.arrange());
        requireParam("mapX", req.mapX());
        requireParam("mapY", req.mapY());
        requireParam("radius", req.radius());
        params.put("mapX", req.mapX());
        params.put("mapY", req.mapY());
        params.put("radius", req.radius());
        putIfPresent(params, "contentTypeId", req.contentTypeId());
        putIfPresent(params, "modifiedtime", req.modifiedtime());
        putIfPresent(params, "lDongRegnCd", req.lDongRegnCd());
        putIfPresent(params, "lDongSignguCd", req.lDongSignguCd());
        putIfPresent(params, "lclsSystm1", req.lclsSystm1());
        putIfPresent(params, "lclsSystm2", req.lclsSystm2());
        putIfPresent(params, "lclsSystm3", req.lclsSystm3());
        return listResponse(OP_LOCATION_BASED_LIST, params, req.pageSize(), pageNo);
    }

    /** 키워드로 관광정보를 검색한다. */
    public TourListResponse searchKeyword(KeywordSearchRequest req) {
        int pageNo = TourCursor.decodePageNo(req.cursor());
        Map<String, String> params = baseParams(req.pageSize(), pageNo, req.arrange());
        requireParam("keyword", req.keyword());
        params.put("keyword", req.keyword());
        putIfPresent(params, "lDongRegnCd", req.lDongRegnCd());
        putIfPresent(params, "lDongSignguCd", req.lDongSignguCd());
        putIfPresent(params, "lclsSystm1", req.lclsSystm1());
        putIfPresent(params, "lclsSystm2", req.lclsSystm2());
        putIfPresent(params, "lclsSystm3", req.lclsSystm3());
        return listResponse(OP_SEARCH_KEYWORD, params, req.pageSize(), pageNo);
    }

    /** 행사/공연/축제 정보를 날짜로 조회한다. */
    public TourListResponse searchFestival(FestivalSearchRequest req) {
        int pageNo = TourCursor.decodePageNo(req.cursor());
        Map<String, String> params = baseParams(req.pageSize(), pageNo, req.arrange());
        requireParam("eventStartDate", req.eventStartDate());
        params.put("eventStartDate", req.eventStartDate());
        putIfPresent(params, "eventEndDate", req.eventEndDate());
        putIfPresent(params, "modifiedtime", req.modifiedtime());
        putIfPresent(params, "lDongRegnCd", req.lDongRegnCd());
        putIfPresent(params, "lDongSignguCd", req.lDongSignguCd());
        putIfPresent(params, "lclsSystm1", req.lclsSystm1());
        putIfPresent(params, "lclsSystm2", req.lclsSystm2());
        putIfPresent(params, "lclsSystm3", req.lclsSystm3());
        return listResponse(OP_SEARCH_FESTIVAL, params, req.pageSize(), pageNo);
    }

    /** 숙박 정보 목록을 조회한다. */
    public TourListResponse searchStay(StaySearchRequest req) {
        int pageNo = TourCursor.decodePageNo(req.cursor());
        Map<String, String> params = baseParams(req.pageSize(), pageNo, req.arrange());
        putIfPresent(params, "modifiedtime", req.modifiedtime());
        putIfPresent(params, "lDongRegnCd", req.lDongRegnCd());
        putIfPresent(params, "lDongSignguCd", req.lDongSignguCd());
        putIfPresent(params, "lclsSystm1", req.lclsSystm1());
        putIfPresent(params, "lclsSystm2", req.lclsSystm2());
        putIfPresent(params, "lclsSystm3", req.lclsSystm3());
        return listResponse(OP_SEARCH_STAY, params, req.pageSize(), pageNo);
    }

    /** 콘텐츠의 공통 상세정보(개요, 홈페이지, 주소, 좌표 등)를 조회한다. */
    public Map<String, String> detailCommon(String contentId) {
        requireParam("contentId", contentId);
        Map<String, String> params = baseParams(1, 1, null);
        params.put("contentId", contentId);
        return detailFields(call("detailCommon2", params));
    }

    /** 콘텐츠 타입별 소개 상세정보(이용시간, 휴무일, 주차 등)를 조회한다. */
    public Map<String, String> detailIntro(String contentId, String contentTypeId) {
        requireParam("contentId", contentId);
        requireParam("contentTypeId", contentTypeId);
        Map<String, String> params = baseParams(1, 1, null);
        params.put("contentId", contentId);
        params.put("contentTypeId", contentTypeId);
        return detailFields(call("detailIntro2", params));
    }

    /** 도메인 관련 도메인 업무를 처리합니다. */
    public TourPlaceDetailResponse detail(String contentId, String contentTypeId) {
        return new TourPlaceDetailResponse(
                contentId,
                contentTypeId,
                detailCommon(contentId),
                detailIntro(contentId, contentTypeId));
    }

    private TourListResponse listResponse(String operation, Map<String, String> params, int pageSize, int pageNo) {
        JsonNode response = call(operation, params);
        JsonNode body = response.path("body");
        JsonNode itemNode = body.path("items").path("item");

        List<TourPlaceResponse> items = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                items.add(toTourPlace(node));
            }
        } else if (itemNode.isObject()) {
            items.add(toTourPlace(itemNode));
        }

        int totalCount = body.path("totalCount").asInt(0);
        String nextCursor = (pageNo * (long) pageSize < totalCount)
                ? TourCursor.encode(pageNo + 1)
                : null;
        return new TourListResponse(items, totalCount, pageSize, nextCursor);
    }

    private Map<String, String> detailFields(JsonNode response) {
        JsonNode body = response.path("body");
        JsonNode itemNode = body.path("items").path("item");
        JsonNode item = itemNode.isArray()
                ? (itemNode.isEmpty() ? null : itemNode.get(0))
                : itemNode.isObject() ? itemNode : null;
        if (item == null) {
            return Map.of();
        }

        Map<String, String> fields = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : item.properties()) {
            if (!entry.getValue().isNull()) {
                fields.put(entry.getKey(), entry.getValue().asString());
            }
        }
        return Map.copyOf(fields);
    }

    private Map<String, String> baseParams(int pageSize, int pageNo, String arrange) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(pageSize));
        params.put("pageNo", String.valueOf(pageNo));
        params.put("MobileOS", "ETC");
        params.put("MobileApp", mobileApp);
        params.put("_type", "json");
        params.put("serviceKey", ensureServiceKey());
        putIfPresent(params, "arrange", arrange);
        return params;
    }

    private JsonNode call(String operation, Map<String, String> params) {
        URI uri = buildUri(operation, params);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(TourApiErrorCode.TOUR_API_INTERRUPTED);
        } catch (IOException e) {
            log.warn("관광 API 호출 실패: {} - {}", operation, e.getMessage());
            throw new BusinessException(TourApiErrorCode.TOUR_API_FAILED);
        }

        if (response.statusCode() == 429) {
            throw new BusinessException(TourApiErrorCode.TOUR_API_RATE_LIMITED);
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new BusinessException(TourApiErrorCode.TOUR_API_RESPONSE_INVALID);
        }

        // 공공데이터포털 오류는 _type=json 여부와 무관하게 XML로만 내려온다.
        String trimmed = body.stripLeading();
        if (trimmed.charAt(0) == '<') {
            log.warn("관광 API XML 오류 응답: {}", extractXmlError(body));
            throw new BusinessException(TourApiErrorCode.TOUR_API_RESPONSE_INVALID);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("관광 API 응답 파싱 실패: {} - {}", operation, e.getMessage());
            throw new BusinessException(TourApiErrorCode.TOUR_API_RESPONSE_INVALID);
        }

        JsonNode header = root.path("response").path("header");
        String resultCode = text(header, "resultCode");
        String resultMsg = text(header, "resultMsg");
        if (!RESULT_CODE_OK.equals(resultCode)) {
            log.warn("관광 API 업무 오류: code={}, message={}", resultCode, resultMsg);
            throw new BusinessException(TourApiErrorCode.TOUR_API_RESPONSE_INVALID);
        }
        return root.path("response");
    }

    private URI buildUri(String operation, Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            query.append('=');
            query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(baseUrl + "/" + operation + "?" + query);
    }

    private String ensureServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new BusinessException(TourApiErrorCode.TOUR_API_NOT_CONFIGURED);
        }
        return serviceKey;
    }

    private void requireParam(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(TourApiErrorCode.TOUR_PARAMETER_REQUIRED, name);
        }
    }

    private TourPlaceResponse toTourPlace(JsonNode node) {
        return new TourPlaceResponse(
                text(node, "contentid"),
                text(node, "contenttypeid"),
                text(node, "title"),
                text(node, "addr1"),
                text(node, "addr2"),
                text(node, "zipcode"),
                text(node, "tel"),
                text(node, "firstimage"),
                text(node, "firstimage2"),
                text(node, "mapx"),
                text(node, "mapy"),
                text(node, "mlevel"),
                text(node, "createdtime"),
                text(node, "modifiedtime"),
                text(node, "cpyrhtDivCd"),
                text(node, "lDongRegnCd"),
                text(node, "lDongSignguCd"),
                text(node, "lclsSystm1"),
                text(node, "lclsSystm2"),
                text(node, "lclsSystm3"),
                text(node, "dist"),
                text(node, "eventstartdate"),
                text(node, "eventenddate"),
                text(node, "progresstype"),
                text(node, "festivaltype"));
    }

    private LegalDistrict toLegalDistrict(JsonNode node) {
        return new LegalDistrict(text(node, "code"), text(node, "name"));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString();
    }

    private void putIfPresent(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private String extractXmlError(String xml) {
        String code = extractTag(xml, "returnReasonCode");
        String message = extractTag(xml, "returnAuthMsg");
        if (code.isBlank() && message.isBlank()) {
            return "인증키 또는 요청이 올바르지 않습니다.";
        }
        return code + " " + message;
    }

    private String extractTag(String xml, String tag) {
        String open = "<" + tag + ">";
        String close = "</" + tag + ">";
        int start = xml.indexOf(open);
        int end = xml.indexOf(close);
        if (start < 0 || end < 0 || end <= start) {
            return "";
        }
        return xml.substring(start + open.length(), end).trim();
    }

}
