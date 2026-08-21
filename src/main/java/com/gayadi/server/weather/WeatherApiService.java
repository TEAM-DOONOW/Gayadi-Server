package com.gayadi.server.weather;

import com.gayadi.server.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class WeatherApiService {

    private static final Logger log = LoggerFactory.getLogger(WeatherApiService.class);

    private static final String OP_ULTRA_SRT_NCST = "getUltraSrtNcst";
    private static final String OP_ULTRA_SRT_FCST = "getUltraSrtFcst";
    private static final String OP_VILAGE_FCST = "getVilageFcst";
    private static final String OP_FCST_VERSION = "getFcstVersion";
    private static final String RESULT_CODE_OK = "00";

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmm");

    private static final int[] VILAGE_BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String serviceKey;

    public WeatherApiService(
            ObjectMapper objectMapper,
            @Value("${tour.api.key:}") String serviceKey,
            @Value("${weather.api.base-url:http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String baseUrl) {
        this.objectMapper = objectMapper;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
    }

    /** 초단기실황 조회 — 현재 날씨 관측값. */
    public UltraSrtNcstResponse ultraSrtNcst(WeatherRequest req) {
        int[] grid = resolveGrid(req);
        String baseDate = req.baseDate() != null && !req.baseDate().isBlank()
                ? req.baseDate() : latestUltraNcstDate();
        String baseTime = req.baseTime() != null && !req.baseTime().isBlank()
                ? req.baseTime() : latestUltraNcstTime();

        Map<String, String> params = baseParams();
        params.put("base_date", baseDate);
        params.put("base_time", baseTime);
        params.put("nx", String.valueOf(grid[0]));
        params.put("ny", String.valueOf(grid[1]));

        JsonNode body = call(OP_ULTRA_SRT_NCST, params).path("body");
        List<JsonNode> items = itemsOf(body);

        Map<String, String> obs = new TreeMap<>();
        for (JsonNode item : items) {
            obs.put(text(item, "category"), text(item, "obsrValue"));
        }

        return new UltraSrtNcstResponse(
                baseDate, baseTime, grid[0], grid[1],
                obs.getOrDefault("T1H", ""),
                translateRain(obs.getOrDefault("RN1", "")),
                obs.getOrDefault("UUU", ""),
                obs.getOrDefault("VVV", ""),
                obs.getOrDefault("REH", ""),
                obs.getOrDefault("PTY", ""),
                translatePrecipType(obs.getOrDefault("PTY", ""), true),
                obs.getOrDefault("VEC", ""),
                translateWindDir(obs.getOrDefault("VEC", "")),
                obs.getOrDefault("WSD", ""),
                translateWindSpeedQualitative(obs.getOrDefault("WSD", "")));
    }

    /** 초단기예보 조회 — 예보시점부터 6시간 이내 예보. */
    public ForecastResponse ultraSrtFcst(WeatherRequest req) {
        int[] grid = resolveGrid(req);
        String[] dt = resolveBaseDateTime(req, ForecastType.ULTRA_FCST);
        Map<String, String> params = baseParams();
        params.put("base_date", dt[0]);
        params.put("base_time", dt[1]);
        params.put("nx", String.valueOf(grid[0]));
        params.put("ny", String.valueOf(grid[1]));

        JsonNode body = call(OP_ULTRA_SRT_FCST, params).path("body");
        return toForecastResponse(dt[0], dt[1], grid, body, true);
    }

    /** 단기예보 조회 — 3~5일 기간 예보. */
    public ForecastResponse vilageFcst(WeatherRequest req) {
        int[] grid = resolveGrid(req);
        String[] dt = resolveBaseDateTime(req, ForecastType.VILAGE_FCST);
        Map<String, String> params = baseParams();
        params.put("base_date", dt[0]);
        params.put("base_time", dt[1]);
        params.put("nx", String.valueOf(grid[0]));
        params.put("ny", String.valueOf(grid[1]));

        JsonNode body = call(OP_VILAGE_FCST, params).path("body");
        return toForecastResponse(dt[0], dt[1], grid, body, false);
    }

    /** 예보버전 조회. */
    public FcstVersionResponse fcstVersion(FcstVersionRequest req) {
        requireParam("ftype", req.ftype());
        requireParam("baseDateTime", req.baseDateTime());

        Map<String, String> params = baseParams();
        params.put("ftype", req.ftype());
        params.put("basedatetime", req.baseDateTime());

        JsonNode body = call(OP_FCST_VERSION, params).path("body");
        List<JsonNode> items = itemsOf(body);
        List<FcstVersionResponse.Item> versions = new ArrayList<>();
        for (JsonNode item : items) {
            versions.add(new FcstVersionResponse.Item(
                    text(item, "filetype"), text(item, "version")));
        }
        return new FcstVersionResponse(versions);
    }

    // --- response parsing ---

    private ForecastResponse toForecastResponse(
            String baseDate, String baseTime, int[] grid, JsonNode body, boolean ultra) {
        List<JsonNode> items = itemsOf(body);

        // group by fcstDate + fcstTime → Map<category, value>
        Map<String, Map<String, String>> bySlot = new TreeMap<>();
        for (JsonNode item : items) {
            String slot = text(item, "fcstDate") + text(item, "fcstTime");
            bySlot.computeIfAbsent(slot, k -> new TreeMap<>())
                    .put(text(item, "category"), text(item, "fcstValue"));
        }

        List<ForecastResponse.Slot> forecast = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : bySlot.entrySet()) {
            String slot = entry.getKey();
            Map<String, String> v = entry.getValue();
            forecast.add(new ForecastResponse.Slot(
                    slot.substring(0, 8),
                    slot.substring(8),
                    v.getOrDefault("TMP", v.getOrDefault("T1H", "")),
                    translateRain(v.getOrDefault("RN1", v.getOrDefault("PCP", ""))),
                    v.getOrDefault("SKY", ""),
                    translateSky(v.getOrDefault("SKY", "")),
                    v.getOrDefault("UUU", ""),
                    v.getOrDefault("VVV", ""),
                    v.getOrDefault("REH", ""),
                    v.getOrDefault("PTY", ""),
                    translatePrecipType(v.getOrDefault("PTY", ""), ultra),
                    v.getOrDefault("POP", ""),
                    v.getOrDefault("LGT", ""),
                    v.getOrDefault("VEC", ""),
                    translateWindDir(v.getOrDefault("VEC", "")),
                    v.getOrDefault("WSD", ""),
                    translateWindSpeedQualitative(v.getOrDefault("WSD", "")),
                    translateSnow(v.getOrDefault("SNO", "")),
                    v.getOrDefault("TMN", ""),
                    v.getOrDefault("TMX", ""),
                    v.getOrDefault("WAV", "")));
        }

        return new ForecastResponse(baseDate, baseTime, grid[0], grid[1], forecast);
    }

    // --- grid resolution ---

    private int[] resolveGrid(WeatherRequest req) {
        if (req.nx() != null && req.ny() != null) {
            return new int[]{req.nx(), req.ny()};
        }
        if (req.lat() != null && req.lon() != null) {
            KmaGridConverter.GridPoint gp = KmaGridConverter.toGrid(req.lon(), req.lat());
            return new int[]{gp.nx(), gp.ny()};
        }
        throw new ApiException(HttpStatus.BAD_REQUEST,
                "위치 정보가 필요합니다. lat/lon 또는 nx/ny 중 하나를 지정하세요.");
    }

    // --- base_date / base_time auto-calculation ---

    private enum ForecastType { ULTRA_NCST, ULTRA_FCST, VILAGE_FCST }

    private String[] resolveBaseDateTime(WeatherRequest req, ForecastType type) {
        if (req.baseDate() != null && !req.baseDate().isBlank()
                && req.baseTime() != null && !req.baseTime().isBlank()) {
            return new String[]{req.baseDate(), req.baseTime()};
        }
        LocalDateTime now = LocalDateTime.now(KST);
        return switch (type) {
            case ULTRA_NCST -> {
                LocalDateTime base = now.minusMinutes(10).withMinute(0).withSecond(0).withNano(0);
                yield new String[]{base.format(DATE_FMT), base.format(TIME_FMT)};
            }
            case ULTRA_FCST -> {
                LocalDateTime base;
                if (now.getMinute() >= 45) {
                    base = now.withMinute(30).withSecond(0).withNano(0);
                } else {
                    base = now.minusHours(1).withMinute(30).withSecond(0).withNano(0);
                }
                yield new String[]{base.format(DATE_FMT), base.format(TIME_FMT)};
            }
            case VILAGE_FCST -> {
                LocalDateTime available = now.minusMinutes(10);
                int hour = available.getHour();
                int baseHour = -1;
                for (int h : VILAGE_BASE_HOURS) {
                    if (h <= hour) baseHour = h;
                }
                LocalDateTime base;
                if (baseHour == -1) {
                    base = available.minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0);
                } else {
                    base = available.withHour(baseHour).withMinute(0).withSecond(0).withNano(0);
                }
                yield new String[]{base.format(DATE_FMT), base.format(TIME_FMT)};
            }
        };
    }

    private String latestUltraNcstDate() {
        return resolveBaseDateTime(null, ForecastType.ULTRA_NCST)[0];
    }

    private String latestUltraNcstTime() {
        return resolveBaseDateTime(null, ForecastType.ULTRA_NCST)[1];
    }

    // --- KMA API call ---

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
            if ("03".equals(resultCode)) {
                throw new ApiException(HttpStatus.NOT_FOUND,
                        "해당 시간의 기상 데이터가 없습니다. 발표 시각을 확인하세요.");
            }
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "기상청 API 오류(" + resultCode + "): " + resultMsg);
        }
        return root.path("response");
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

    private Map<String, String> baseParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("numOfRows", "1000");
        params.put("pageNo", "1");
        params.put("dataType", "JSON");
        params.put("serviceKey", ensureServiceKey());
        return params;
    }

    private List<JsonNode> itemsOf(JsonNode body) {
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

    private String ensureServiceKey() {
        if (serviceKey == null || serviceKey.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "기상청 API 키(TOUR_API_KEY)가 설정되지 않았습니다.");
        }
        return serviceKey;
    }

    private void requireParam(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "필수 파라미터 " + name + "이(가) 없습니다.");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    // --- category code translation ---

    private String translateSky(String code) {
        if (code.isBlank()) return "";
        return switch (code) {
            case "1" -> "맑음";
            case "3" -> "구름많음";
            case "4" -> "흐림";
            default -> code;
        };
    }

    private String translatePrecipType(String code, boolean ultra) {
        if (code.isBlank()) return "";
        if (ultra) {
            return switch (code) {
                case "0" -> "없음";
                case "1" -> "비";
                case "2" -> "비/눈";
                case "3" -> "눈";
                case "4" -> "소나기";
                case "5" -> "빗방울";
                case "6" -> "빗방울눈날림";
                case "7" -> "눈날림";
                default -> code;
            };
        }
        return switch (code) {
            case "0" -> "없음";
            case "1" -> "비";
            case "2" -> "비/눈";
            case "3" -> "눈";
            case "4" -> "소나기";
            default -> code;
        };
    }

    private String translateRain(String raw) {
        if (raw == null || raw.isBlank() || "0".equals(raw) || "-".equals(raw) || "null".equalsIgnoreCase(raw)) {
            return "강수없음";
        }
        try {
            double f = Double.parseDouble(raw);
            if (f < 1.0) return "1mm 미만";
            if (f < 30.0) return raw + "mm";
            if (f < 50.0) return "30.0~50.0mm";
            return "50.0mm 이상";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private String translateSnow(String raw) {
        if (raw == null || raw.isBlank() || "0".equals(raw) || "-".equals(raw) || "null".equalsIgnoreCase(raw)) {
            return "적설없음";
        }
        try {
            double f = Double.parseDouble(raw);
            if (f < 0.5) return "0.5cm 미만";
            if (f < 5.0) return raw + "cm";
            return "5.0cm 이상";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    private String translateWindDir(String raw) {
        if (raw.isBlank()) return "";
        try {
            int deg = (int) Double.parseDouble(raw);
            int idx = (int) ((deg + 22.5 * 0.5) / 22.5) % 16;
            String[] names = {"N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                    "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};
            return names[idx];
        } catch (NumberFormatException e) {
            return "";
        }
    }

    private String translateWindSpeedQualitative(String raw) {
        if (raw.isBlank()) return "";
        try {
            double f = Double.parseDouble(raw);
            if (f < 4.0) return "약한 바람";
            if (f < 9.0) return "약간 강한 바람";
            return "강한 바람";
        } catch (NumberFormatException e) {
            if ("1".equals(raw)) return "약한 바람";
            if ("2".equals(raw)) return "약간 강한 바람";
            if ("3".equals(raw)) return "강한 바람";
            return raw;
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

    // --- DTOs ---

    public record WeatherRequest(
            Double lat, Double lon,
            Integer nx, Integer ny,
            String baseDate, String baseTime) {
    }

    public record UltraSrtNcstResponse(
            String baseDate, String baseTime, int nx, int ny,
            String temperature,
            String hourlyPrecipitation,
            String eastWestWind,
            String northSouthWind,
            String humidity,
            String precipitationType,
            String precipitationTypeName,
            String windDirection,
            String windDirectionName,
            String windSpeed,
            String windSpeedName) {
    }

    public record ForecastResponse(
            String baseDate, String baseTime, int nx, int ny,
            List<Slot> forecast) {

        public record Slot(
                String fcstDate, String fcstTime,
                String temperature,
                String hourlyPrecipitation,
                String sky,
                String skyName,
                String eastWestWind,
                String northSouthWind,
                String humidity,
                String precipitationType,
                String precipitationTypeName,
                String precipitationProbability,
                String lightning,
                String windDirection,
                String windDirectionName,
                String windSpeed,
                String windSpeedName,
                String snow,
                String minTemperature,
                String maxTemperature,
                String waveHeight) {
        }
    }

    public record FcstVersionRequest(String ftype, String baseDateTime) {
    }

    public record FcstVersionResponse(List<Item> items) {
        public record Item(String fileType, String version) {
        }
    }
}
