package com.gayadi.server.weather;

import com.gayadi.server.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
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
@Component
public class WeatherApiClient {

    private static final Logger log = LoggerFactory.getLogger(WeatherApiClient.class);

    private static final String RESULT_CODE_OK = "00";
    private static final String RESULT_CODE_NO_DATA = "03";
    private final HttpClient client;

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceKey;
    private final Duration requestTimeout;
    private final int pageSize;
    private final int maximumPages;

    @Autowired
    WeatherApiClient(ObjectMapper objectMapper, WeatherApiProperties properties) {
        this(objectMapper, properties.key(), properties.baseUrl(), properties.connectTimeout(),
                properties.requestTimeout(), properties.pageSize(), properties.maximumPages());
    }

    public WeatherApiClient(
            ObjectMapper objectMapper,
            String serviceKey,
            String baseUrl) {
        this(objectMapper, serviceKey, baseUrl, Duration.ofSeconds(5), Duration.ofSeconds(10),
                1000, 20);
    }

    WeatherApiClient(
            ObjectMapper objectMapper, String serviceKey, String baseUrl,
            Duration connectTimeout, Duration requestTimeout, int pageSize, int maximumPages) {
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.requestTimeout = requestTimeout;
        this.pageSize = pageSize;
        this.maximumPages = maximumPages;
        this.client = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    }

    /** 지정한 오퍼레이션을 호출해 response JSON 노드를 반환한다. */
    JsonNode call(String operation, Map<String, String> params) {
        URI uri = buildUri(operation, params);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(requestTimeout)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.BAD_GATEWAY, "기상청 API 호출이 중단되었습니다.");
        } catch (IOException e) {
            log.warn("기상청 API 호출 실패: {} - {}", operation, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "기상청 API 호출에 실패했습니다.");
        }

        if (response.statusCode() == 429) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "기상청 API 호출 한도를 초과했습니다. 잠시 후 다시 시도해 주세요.");
        }

        String body = response.body();
        if (body == null || body.isBlank()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "기상청 API 응답이 비어 있습니다.");
        }

        String trimmed = body.stripLeading();
        if (trimmed.charAt(0) == '<') {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "기상청 API 호출이 거부되었습니다: " + extractXmlError(body));
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "기상청 API 인증에 실패했습니다.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "기상청 API가 오류를 반환했습니다.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            log.warn("기상청 API 응답 파싱 실패: {} - {}", operation, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "기상청 API 응답을 해석하지 못했습니다.");
        }

        JsonNode header = root.path("response").path("header");
        String resultCode = header.path("resultCode").asText("");
        String resultMsg = header.path("resultMsg").asText("");
        if (!RESULT_CODE_OK.equals(resultCode)) {
            if (RESULT_CODE_NO_DATA.equals(resultCode)) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "해당 시간의 기상 데이터가 없습니다. 발표 시각을 확인하세요.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "기상청 API 오류(" + resultCode + "): " + resultMsg);
        }
        return root.path("response");
    }

    /** 전체 건수가 한 페이지보다 많아도 마지막 페이지까지 안전하게 수집합니다. */
    List<JsonNode> allItems(String operation, Map<String, String> params) {
        Map<String, String> pageParams = new LinkedHashMap<>(params);
        List<JsonNode> result = new ArrayList<>();
        int page = 1;
        int totalCount = Integer.MAX_VALUE;

        while (result.size() < totalCount && page <= maximumPages) {
            pageParams.put("pageNo", String.valueOf(page));
            JsonNode body = call(operation, pageParams).path("body");
            List<JsonNode> pageItems = itemsOf(body);
            totalCount = Math.max(0, body.path("totalCount").asInt(pageItems.size()));
            result.addAll(pageItems);

            if (result.size() >= totalCount) return List.copyOf(result);
            if (pageItems.isEmpty()) {
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "기상청 API의 페이지 응답이 완전하지 않습니다.");
            }
            page++;
        }

        if (result.size() < totalCount) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "기상청 API의 전체 예보 데이터를 가져오지 못했습니다.");
        }
        return List.copyOf(result);
    }

    Map<String, String> baseParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", String.valueOf(pageSize));
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
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private URI buildUri(String operation, Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!query.isEmpty()) query.append('&');
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            query.append('=');
            query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return URI.create(baseUrl + "/" + operation + "?" + query);
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private String ensureServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "기상청 API 키(WEATHER_API_KEY)가 설정되지 않았습니다.");
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
