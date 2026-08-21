package com.gayadi.server.weather;

import com.gayadi.server.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class WeatherApiService {

    private static final String OP_ULTRA_SRT_NCST = "getUltraSrtNcst";
    private static final String OP_ULTRA_SRT_FCST = "getUltraSrtFcst";
    private static final String OP_VILAGE_FCST = "getVilageFcst";
    private static final String OP_FCST_VERSION = "getFcstVersion";

    private final WeatherApiClient client;

    public WeatherApiService(
            ObjectMapper objectMapper,
            @Value("${tour.api.key:}") String serviceKey,
            @Value("${weather.api.base-url:http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0}") String baseUrl) {
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

        JsonNode body = client.call(OP_ULTRA_SRT_NCST, params).path("body");
        Map<String, String> obs = extractObservations(client.itemsOf(body));

        return new UltraSrtNcstResponse(
                dt.date(), dt.time(), grid[0], grid[1],
                obs.getOrDefault(WeatherCategory.T1H.name(), ""),
                WeatherCodeTranslator.rain(obs.getOrDefault(WeatherCategory.RN1.name(), "")),
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

        Map<String, String> params = client.baseParams();
        params.put("ftype", req.ftype());
        params.put("basedatetime", req.baseDateTime());

        JsonNode body = client.call(OP_FCST_VERSION, params).path("body");
        List<FcstVersionResponse.Item> versions = new ArrayList<>();
        for (JsonNode item : client.itemsOf(body)) {
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

        JsonNode body = client.call(operation, params).path("body");
        List<ForecastResponse.Slot> forecast = toSlots(client.itemsOf(body), ultra);
        return new ForecastResponse(dt.date(), dt.time(), grid[0], grid[1], forecast);
    }

    private List<ForecastResponse.Slot> toSlots(List<JsonNode> items, boolean ultra) {
        Map<String, Map<String, String>> bySlot = new TreeMap<>();
        for (JsonNode item : items) {
            String slot = WeatherApiClient.text(item, "fcstDate")
                    + WeatherApiClient.text(item, "fcstTime");
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

    private void requireParam(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "필수 파라미터 " + name + "이(가) 없습니다.");
        }
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
