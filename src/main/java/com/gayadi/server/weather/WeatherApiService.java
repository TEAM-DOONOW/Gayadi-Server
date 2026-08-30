package com.gayadi.server.weather;

import com.gayadi.server.common.exception.BusinessException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class WeatherApiService {

    private static final String OP_ULTRA_SRT_NCST = "getUltraSrtNcst";
    private static final String OP_ULTRA_SRT_FCST = "getUltraSrtFcst";
    private static final String OP_VILAGE_FCST = "getVilageFcst";
    private static final String OP_FCST_VERSION = "getFcstVersion";
    private static final Set<String> VERSION_FILE_TYPES = Set.of("ODAM", "VSRT", "SHRT");

    private final WeatherApiClient client;

    public WeatherApiService(
            ObjectMapper objectMapper,
            @Value("${weather.api.key:}") String serviceKey,
            @Value("${weather.api.base-url:https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String baseUrl) {
        this.client = new WeatherApiClient(objectMapper, serviceKey, baseUrl);
    }

    /** 초단기실황 조회 — 현재 날씨 관측값. */
    public UltraSrtNcstResponse ultraSrtNcst(WeatherRequest req) {
        int[] grid = resolveGrid(req);
        KmaBaseTimeCalculator.BaseDateTime dt = KmaBaseTimeCalculator.resolve(
                KmaBaseTimeCalculator.ForecastType.ULTRA_NCST, req.baseDate(), req.baseTime());

        Map<String, String> params = client.baseParams();
        params.put("base_date", dt.date());
        params.put("base_time", dt.time());
        params.put("nx", String.valueOf(grid[0]));
        params.put("ny", String.valueOf(grid[1]));

        Map<String, String> obs = extractObservations(client.allItems(OP_ULTRA_SRT_NCST, params));
        String rawPrecipitation = obs.getOrDefault(WeatherCategory.RN1.name(), "");

        return new UltraSrtNcstResponse(
                dt.date(), dt.time(), grid[0], grid[1],
                obs.getOrDefault(WeatherCategory.T1H.name(), ""),
                rawPrecipitation,
                WeatherCodeTranslator.rain(rawPrecipitation),
                obs.getOrDefault(WeatherCategory.UUU.name(), ""),
                obs.getOrDefault(WeatherCategory.VVV.name(), ""),
                obs.getOrDefault(WeatherCategory.REH.name(), ""),
                obs.getOrDefault(WeatherCategory.PTY.name(), ""),
                WeatherCodeTranslator.precipType(obs.getOrDefault(WeatherCategory.PTY.name(), ""), true),
                obs.getOrDefault(WeatherCategory.VEC.name(), ""),
                WeatherCodeTranslator.windDirection(obs.getOrDefault(WeatherCategory.VEC.name(), "")),
                obs.getOrDefault(WeatherCategory.WSD.name(), ""),
                WeatherCodeTranslator.windSpeed(obs.getOrDefault(WeatherCategory.WSD.name(), "")));
    }

    /** 초단기예보 조회 — 예보시점부터 6시간 이내 예보. */
    public ForecastResponse ultraSrtFcst(WeatherRequest req) {
        return forecast(OP_ULTRA_SRT_FCST, req,
                KmaBaseTimeCalculator.ForecastType.ULTRA_FCST, true);
    }

    /** 단기예보 조회 — 3~5일 기간 예보. */
    public ForecastResponse vilageFcst(WeatherRequest req) {
        return forecast(OP_VILAGE_FCST, req,
                KmaBaseTimeCalculator.ForecastType.VILAGE_FCST, false);
    }

    /** 예보버전 조회. */
    public FcstVersionResponse fcstVersion(FcstVersionRequest req) {
        requireParam("ftype", req.ftype());
        requireParam("baseDateTime", req.baseDateTime());
        String fileType = req.ftype().trim().toUpperCase(java.util.Locale.ROOT);
        validateVersionRequest(fileType, req.baseDateTime().trim());

        Map<String, String> params = client.baseParams();
        params.put("ftype", fileType);
        params.put("basedatetime", req.baseDateTime().trim());

        List<FcstVersionResponse.Item> versions = new ArrayList<>();
        for (JsonNode item : client.allItems(OP_FCST_VERSION, params)) {
            versions.add(new FcstVersionResponse.Item(
                    WeatherApiClient.text(item, "filetype"),
                    WeatherApiClient.text(item, "version")));
        }
        return new FcstVersionResponse(versions);
    }

    // --- internal ---

    private ForecastResponse forecast(
            String operation, WeatherRequest req,
            KmaBaseTimeCalculator.ForecastType type, boolean ultra) {
        int[] grid = resolveGrid(req);
        KmaBaseTimeCalculator.BaseDateTime dt = KmaBaseTimeCalculator.resolve(
                type, req.baseDate(), req.baseTime());

        Map<String, String> params = client.baseParams();
        params.put("base_date", dt.date());
        params.put("base_time", dt.time());
        params.put("nx", String.valueOf(grid[0]));
        params.put("ny", String.valueOf(grid[1]));

        List<ForecastResponse.Slot> forecast = toSlots(client.allItems(operation, params), ultra);
        return new ForecastResponse(dt.date(), dt.time(), grid[0], grid[1], forecast);
    }

    private List<ForecastResponse.Slot> toSlots(List<JsonNode> items, boolean ultra) {
        Map<String, Map<String, String>> bySlot = new TreeMap<>();
        for (JsonNode item : items) {
            String slot = WeatherApiClient.text(item, "fcstDate")
                    + WeatherApiClient.text(item, "fcstTime");
            if (!slot.matches("\\d{12}")) {
                throw new BusinessException(WeatherErrorCode.WEATHER_API_RESPONSE_INVALID);
            }
            bySlot.computeIfAbsent(slot, k -> new TreeMap<>())
                    .put(WeatherApiClient.text(item, "category"),
                            WeatherApiClient.text(item, "fcstValue"));
        }

        List<ForecastResponse.Slot> forecast = new ArrayList<>();
        for (Map.Entry<String, Map<String, String>> entry : bySlot.entrySet()) {
            String slot = entry.getKey();
            Map<String, String> v = entry.getValue();
            forecast.add(new ForecastResponse.Slot(
                    slot.substring(0, 8),
                    slot.substring(8),
                    v.getOrDefault(WeatherCategory.TMP.name(),
                            v.getOrDefault(WeatherCategory.T1H.name(), "")),
                    WeatherCodeTranslator.rain(v.getOrDefault(WeatherCategory.RN1.name(),
                            v.getOrDefault(WeatherCategory.PCP.name(), ""))),
                    v.getOrDefault(WeatherCategory.SKY.name(), ""),
                    WeatherCodeTranslator.sky(v.getOrDefault(WeatherCategory.SKY.name(), "")),
                    v.getOrDefault(WeatherCategory.UUU.name(), ""),
                    v.getOrDefault(WeatherCategory.VVV.name(), ""),
                    v.getOrDefault(WeatherCategory.REH.name(), ""),
                    v.getOrDefault(WeatherCategory.PTY.name(), ""),
                    WeatherCodeTranslator.precipType(
                            v.getOrDefault(WeatherCategory.PTY.name(), ""), ultra),
                    v.getOrDefault(WeatherCategory.POP.name(), ""),
                    v.getOrDefault(WeatherCategory.LGT.name(), ""),
                    v.getOrDefault(WeatherCategory.VEC.name(), ""),
                    WeatherCodeTranslator.windDirection(
                            v.getOrDefault(WeatherCategory.VEC.name(), "")),
                    v.getOrDefault(WeatherCategory.WSD.name(), ""),
                    WeatherCodeTranslator.windSpeed(
                            v.getOrDefault(WeatherCategory.WSD.name(), "")),
                    WeatherCodeTranslator.snow(v.getOrDefault(WeatherCategory.SNO.name(), "")),
                    v.getOrDefault(WeatherCategory.TMN.name(), ""),
                    v.getOrDefault(WeatherCategory.TMX.name(), ""),
                    v.getOrDefault(WeatherCategory.WAV.name(), "")));
        }
        return forecast;
    }

    private Map<String, String> extractObservations(List<JsonNode> items) {
        Map<String, String> obs = new TreeMap<>();
        for (JsonNode item : items) {
            obs.put(WeatherApiClient.text(item, "category"),
                    WeatherApiClient.text(item, "obsrValue"));
        }
        return obs;
    }

    private int[] resolveGrid(WeatherRequest req) {
        boolean hasAnyGrid = req.nx() != null || req.ny() != null;
        boolean hasAllGrid = req.nx() != null && req.ny() != null;
        boolean hasAnyCoordinate = req.lat() != null || req.lon() != null;
        boolean hasAllCoordinates = req.lat() != null && req.lon() != null;

        if ((hasAnyGrid && !hasAllGrid) || (hasAnyCoordinate && !hasAllCoordinates)) {
            throw new BusinessException(WeatherErrorCode.WEATHER_LOCATION_PAIR_REQUIRED);
        }
        if (hasAllGrid && hasAllCoordinates) {
            throw new BusinessException(WeatherErrorCode.WEATHER_LOCATION_TYPE_CONFLICT);
        }
        if (hasAllGrid) {
            if (req.nx() < 1 || req.nx() > KmaGridConverter.NX
                    || req.ny() < 1 || req.ny() > KmaGridConverter.NY) {
                throw new BusinessException(WeatherErrorCode.WEATHER_GRID_OUT_OF_RANGE);
            }
            return new int[]{req.nx(), req.ny()};
        }
        if (hasAllCoordinates) {
            if (!Double.isFinite(req.lat()) || !Double.isFinite(req.lon())
                    || req.lat() < -90 || req.lat() > 90
                    || req.lon() < -180 || req.lon() > 180) {
                throw new BusinessException(WeatherErrorCode.WEATHER_COORDINATE_OUT_OF_RANGE);
            }
            KmaGridConverter.GridPoint gp = KmaGridConverter.toGrid(req.lon(), req.lat());
            if (gp.nx() < 1 || gp.nx() > KmaGridConverter.NX
                    || gp.ny() < 1 || gp.ny() > KmaGridConverter.NY) {
                throw new BusinessException(WeatherErrorCode.WEATHER_LOCATION_UNSUPPORTED);
            }
            return new int[]{gp.nx(), gp.ny()};
        }
        throw new BusinessException(WeatherErrorCode.WEATHER_LOCATION_REQUIRED);
    }

    private void requireParam(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(WeatherErrorCode.WEATHER_REQUIRED_PARAMETER_MISSING, name);
        }
    }

    private void validateVersionRequest(String fileType, String baseDateTime) {
        if (!VERSION_FILE_TYPES.contains(fileType)) {
            throw new BusinessException(WeatherErrorCode.WEATHER_VERSION_FILE_TYPE_INVALID);
        }
        if (!baseDateTime.matches("\\d{12}")) {
            throw invalidVersionDateTime();
        }
        int hour = Integer.parseInt(baseDateTime.substring(8, 10));
        int minute = Integer.parseInt(baseDateTime.substring(10));
        if (hour > 23 || minute > 59) throw invalidVersionDateTime();
        try {
            LocalDate date = LocalDate.parse(
                    baseDateTime.substring(0, 8), DateTimeFormatter.BASIC_ISO_DATE);
            LocalTime time = LocalTime.parse(
                    baseDateTime.substring(8), DateTimeFormatter.ofPattern("HHmm"));
            LocalDateTime.of(date, time);
        } catch (DateTimeParseException exception) {
            throw invalidVersionDateTime();
        }
    }

    private BusinessException invalidVersionDateTime() {
        return new BusinessException(WeatherErrorCode.WEATHER_VERSION_DATETIME_INVALID);
    }

    // --- DTOs ---

    public record WeatherRequest(
            Double lat, Double lon,
            Integer nx, Integer ny,
            String baseDate, String baseTime) {
    }

    @Schema(name = "UltraShortNowcastResponse", description = "기상청 초단기실황 관측값")
    public record UltraSrtNcstResponse(
            @Schema(description = "발표일자", example = "20260825") String baseDate,
            @Schema(description = "발표시각", example = "1400") String baseTime,
            @Schema(description = "기상청 격자 X", example = "60") int nx,
            @Schema(description = "기상청 격자 Y", example = "127") int ny,
            @Schema(description = "기온(℃)", example = "27.1") String temperature,
            @Schema(description = "기상청 원본 1시간 강수량", example = "0") String hourlyPrecipitationRaw,
            @Schema(description = "1시간 강수량 해석", example = "강수없음") String hourlyPrecipitation,
            @Schema(description = "동서바람 성분(m/s)") String eastWestWind,
            @Schema(description = "남북바람 성분(m/s)") String northSouthWind,
            @Schema(description = "습도(%)", example = "72") String humidity,
            @Schema(description = "강수형태 코드", example = "0") String precipitationType,
            @Schema(description = "강수형태 이름", example = "없음") String precipitationTypeName,
            @Schema(description = "풍향(도)", example = "180") String windDirection,
            @Schema(description = "16방위 풍향", example = "S") String windDirectionName,
            @Schema(description = "풍속(m/s)", example = "2.1") String windSpeed,
            @Schema(description = "풍속 해석", example = "약한 바람") String windSpeedName) {
    }

    @Schema(name = "WeatherForecastResponse", description = "발표시각과 격자별 예보 전체 결과")
    public record ForecastResponse(
            @Schema(description = "발표일자", example = "20260825") String baseDate,
            @Schema(description = "발표시각", example = "1400") String baseTime,
            @Schema(description = "기상청 격자 X", example = "60") int nx,
            @Schema(description = "기상청 격자 Y", example = "127") int ny,
            @Schema(description = "시간순 예보. 모든 API 페이지를 합친 결과") List<Slot> forecast) {

        @Schema(name = "WeatherForecastSlot", description = "한 예보 시각의 카테고리별 값")
        public record Slot(
                @Schema(description = "예보일자", example = "20260826") String fcstDate,
                @Schema(description = "예보시각", example = "1500") String fcstTime,
                @Schema(description = "기온(℃)") String temperature,
                @Schema(description = "1시간 강수량 해석") String hourlyPrecipitation,
                @Schema(description = "하늘상태 코드") String sky,
                @Schema(description = "하늘상태 이름") String skyName,
                @Schema(description = "동서바람 성분(m/s)") String eastWestWind,
                @Schema(description = "남북바람 성분(m/s)") String northSouthWind,
                @Schema(description = "습도(%)") String humidity,
                @Schema(description = "강수형태 코드") String precipitationType,
                @Schema(description = "강수형태 이름") String precipitationTypeName,
                @Schema(description = "강수확률(%)") String precipitationProbability,
                @Schema(description = "낙뢰") String lightning,
                @Schema(description = "풍향(도)") String windDirection,
                @Schema(description = "16방위 풍향") String windDirectionName,
                @Schema(description = "풍속(m/s)") String windSpeed,
                @Schema(description = "풍속 해석") String windSpeedName,
                @Schema(description = "1시간 신적설 해석") String snow,
                @Schema(description = "일 최저기온(℃)") String minTemperature,
                @Schema(description = "일 최고기온(℃)") String maxTemperature,
                @Schema(description = "파고(m)") String waveHeight) {
        }
    }

    public record FcstVersionRequest(String ftype, String baseDateTime) {
    }

    @Schema(name = "WeatherForecastVersionResponse", description = "기상청 예보 파일 버전 목록")
    public record FcstVersionResponse(
            @Schema(description = "예보 파일 버전") List<Item> items) {
        public record Item(
                @Schema(description = "파일 종류", example = "SHRT") String fileType,
                @Schema(description = "버전") String version) {
        }
    }
}
