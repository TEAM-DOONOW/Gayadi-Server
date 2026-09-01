package com.gayadi.server.weather;

import com.gayadi.server.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

/**
 * 기상청 API HTTP 통신 담당 — URI 빌드, 호출, 응답 검증, XML 오러 추출.
 */
class WeatherApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherApiClient.class);

    private static final String RESULT_CODE_OK = "00";
    private static final String RESULT_CODE_NO_DATA = "03";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int DEFAULT_PAGE_SIZE = 1000;
    private static final int MAX_PAGES = 20;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceKey;

    WeatherApiClient(
            ObjectMapper objectMapper,
            String serviceKey,
            String baseUrl) {
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    /** 지정한 오퍼레이션을 호출해 response JSON 노드를 반환한다. */
    JsonNode call(String operation, Map<String, String> params) {
        URI uri = buildUri(operation, params);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(WeatherErrorCode.WEATHER_API_INTERRUPTED);
        } catch (IOException e) {
            log.warn("기상청 API 호출 실패: {} - {}", operation, e.getMessage());
            throw new BusinessException(WeatherErrorCode.WEATHER_API_FAILED);
        }

        if (response.statusCode() == 429) {
            throw new BusinessException(WeatherErrorCode.WEATHER_API_RATE_LIMITED);
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new BusinessException(WeatherErrorCode.WEATHER_API_RESPONSE_INVALID);
        }

        String trimmed = body.stripLeading();
        if (trimmed.charAt(0) == '<') {
            log.warn("기상청 API XML 오류 응답: {}", extractXmlError(body));
            throw new BusinessException(WeatherErrorCode.WEATHER_API_AUTH_FAILED);
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new BusinessException(WeatherErrorCode.WEATHER_API_AUTH_FAILED);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new BusinessException(WeatherErrorCode.WEATHER_API_RESPONSE_INVALID);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("기상청 API 응답 파싱 실패: {} - {}", operation, e.getMessage());
            throw new BusinessException(WeatherErrorCode.WEATHER_API_RESPONSE_INVALID);
        }

        JsonNode header = root.path("response").path("header");
        String resultCode = text(header, "resultCode");
        String resultMsg = text(header, "resultMsg");
        if (!RESULT_CODE_OK.equals(resultCode)) {
            if (RESULT_CODE_NO_DATA.equals(resultCode)) {
                throw new BusinessException(WeatherErrorCode.WEATHER_DATA_NOT_FOUND);
            }
            log.warn("기상청 API 업무 오류: code={}, message={}", resultCode, resultMsg);
            throw new BusinessException(WeatherErrorCode.WEATHER_API_RESPONSE_INVALID);
        }
        return root.path("response");
    }

    /** 전체 건수가 한 페이지보다 많아도 마지막 페이지까지 안전하게 수집합니다. */
    List<JsonNode> allItems(String operation, Map<String, String> params) {
        Map<String, String> pageParams = new LinkedHashMap<>(params);
        List<JsonNode> result = new ArrayList<>();
        int page = 1;
        int totalCount = Integer.MAX_VALUE;

        while (result.size() < totalCount && page <= MAX_PAGES) {
            pageParams.put("pageNo", String.valueOf(page));
            JsonNode body = call(operation, pageParams).path("body");
            List<JsonNode> pageItems = itemsOf(body);
            totalCount = Math.max(0, body.path("totalCount").asInt(pageItems.size()));
            result.addAll(pageItems);

            if (result.size() >= totalCount) {
                return List.copyOf(result);
            }
            if (pageItems.isEmpty()) {
                throw new BusinessException(WeatherErrorCode.WEATHER_API_RESPONSE_INVALID);
            }
            page++;
        }

        if (result.size() < totalCount) {
            throw new BusinessException(WeatherErrorCode.WEATHER_API_RESPONSE_INVALID);
        }
        return List.copyOf(result);
    }

    Map<String, String> baseParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(DEFAULT_PAGE_SIZE));
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("serviceKey", ensureServiceKey());
        return params;
    }

    List<JsonNode> itemsOf(JsonNode body) {
        JsonNode itemNode = body.path("items").path("item");
        List<JsonNode> items = new ArrayList<>();
        if (itemNode.isArray()) {
            for (JsonNode node : itemNode) {
                items.add(node);
            }
        } else if (itemNode.isObject()) {
            items.add(itemNode);
        }
        return items;
    }

    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString();
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
            throw new BusinessException(WeatherErrorCode.WEATHER_API_NOT_CONFIGURED);
        }
        return serviceKey;
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
