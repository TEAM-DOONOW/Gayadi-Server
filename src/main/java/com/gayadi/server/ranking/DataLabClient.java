package com.gayadi.server.ranking;

import com.gayadi.server.common.PublicDataKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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

/**
 * 한국관광 데이터랩 공공데이터(중심 관광지, 지역별 방문자 수)를 조회합니다.
 * 순위는 부가 정보이므로 오류는 예외 대신 빈 목록으로 돌려주고 호출자가 대체 목록을 사용합니다.
 */
@Component
public class DataLabClient {

    private static final Logger log = LoggerFactory.getLogger(DataLabClient.class);
    private static final int MAX_PAGES = 10;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper objectMapper;
    private final String serviceKey;
    private final String hubBaseUrl;
    private final String visitorBaseUrl;
    private final String mobileApp;

    public DataLabClient(
            ObjectMapper objectMapper,
            @Value("${datalab.api.key:}") String serviceKey,
            @Value("${datalab.api.hub-base-url:https://apis.data.go.kr/B551011/LocgoHubTarService1}") String hubBaseUrl,
            @Value("${datalab.api.visitor-base-url:https://apis.data.go.kr/B551011/DataLabService}") String visitorBaseUrl,
            @Value("${datalab.api.mobile-app:Gayadi}") String mobileApp) {
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.hubBaseUrl = stripTrailingSlash(hubBaseUrl);
        this.visitorBaseUrl = stripTrailingSlash(visitorBaseUrl);
        this.mobileApp = mobileApp == null || mobileApp.isBlank() ? "Gayadi" : mobileApp.trim();
    }

    public boolean configured() {
        return !serviceKey.isBlank();
    }

    /** 기초지자체 중심 관광지 순위(hubRank)를 조회합니다. signguCd가 비어 있으면 광역 단위로 요청합니다. */
    public List<JsonNode> hubAttractions(String baseYm, String areaCode, String districtCode) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("baseYm", baseYm);
        params.put("areaCd", areaCode);
        if (districtCode != null && !districtCode.isBlank()) {
            params.put("signguCd", districtCode);
        }
        return fetchAll(hubBaseUrl, "areaBasedList1", params);
    }

    /** 기초지자체별 일별 방문자 수를 조회합니다. 날짜는 yyyyMMdd 형식입니다. */
    public List<JsonNode> districtVisitors(String startYmd, String endYmd) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("startYmd", startYmd);
        params.put("endYmd", endYmd);
        return fetchAll(visitorBaseUrl, "locgoRegnVisitrDDList", params);
    }

    private List<JsonNode> fetchAll(String baseUrl, String operation, Map<String, String> conditions) {
        if (!configured()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>();
        try {
            for (int page = 1; page <= MAX_PAGES; page++) {
                JsonNode body = fetchPage(baseUrl, operation, conditions, page);
                if (body == null) {
                    break;
                }
                JsonNode itemNode = body.path("items").path("item");
                int before = items.size();
                if (itemNode.isArray()) {
                    itemNode.forEach(items::add);
                } else if (itemNode.isObject()) {
                    items.add(itemNode);
                }
                int total = body.path("totalCount").asInt(0);
                if (items.size() == before || items.size() >= total) {
                    break;
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            log.warn("데이터랩 조회 실패: {} - {}", operation, exception.getClass().getSimpleName());
        }
        return List.copyOf(items);
    }

    private JsonNode fetchPage(String baseUrl, String operation, Map<String, String> conditions, int page)
            throws Exception {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("serviceKey", serviceKey);
        params.put("pageNo", String.valueOf(page));
        params.put("numOfRows", "1000");
        params.put("MobileOS", "ETC");
        params.put("MobileApp", mobileApp);
        params.put("_type", "json");
        params.putAll(conditions);
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(buildUri(baseUrl, operation, params))
                        .timeout(Duration.ofSeconds(10))
                        .header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String body = response.body();
        // 공공데이터포털 인증·승인 오류는 _type=json이어도 XML로 내려옵니다.
        if (response.statusCode() != 200 || body == null || body.isBlank()
                || body.stripLeading().startsWith("<")) {
            log.warn("데이터랩 응답 오류: {} status={}", operation, response.statusCode());
            return null;
        }
        JsonNode root = objectMapper.readTree(body);
        JsonNode envelope = root.has("response") ? root.path("response") : root;
        String resultCode = envelope.path("header").path("resultCode").asString("");
        if (!"0000".equals(resultCode) && !"00".equals(resultCode)) {
            log.warn("데이터랩 업무 오류: {} code={}", operation, resultCode);
            return null;
        }
        return envelope.path("body");
    }

    private static URI buildUri(String baseUrl, String operation, Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append("serviceKey".equals(entry.getKey())
                            ? PublicDataKey.queryValue(entry.getValue())
                            : URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(baseUrl + "/" + operation + "?" + query);
    }

    private static String stripTrailingSlash(String value) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
