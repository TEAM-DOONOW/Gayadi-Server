package com.gayadi.server.weather;

import java.util.Map;
import java.util.Set;

/**
 * 기상청 카테고리 코드값 → 한국어 표현 변환.
 * 가이드 코드표 기준 — SKY, PTY, VEC(16방위), WSD, PCP, SNO.
 */
final class WeatherCodeTranslator {

    private static final Map<String, String> SKY_CODES = Map.of(
            "1", "맑음",
            "3", "구름많음",
            "4", "흐림");

    private static final Map<String, String> PTY_ULTRA = Map.of(
            "0", "없음", "1", "비", "2", "비/눈", "3", "눈",
            "4", "소나기", "5", "빗방울", "6", "빗방울눈날림", "7", "눈날림");

    private static final Map<String, String> PTY_VILAGE = Map.of(
            "0", "없음", "1", "비", "2", "비/눈", "3", "눈", "4", "소나기");

    private static final String[] WIND_DIRECTIONS = {
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"};

    private static final Set<String> NO_DATA = Set.of("", "0", "-", "null");

    private static final double RAIN_LIGHT = 1.0;
    private static final double RAIN_HEAVY = 30.0;
    private static final double RAIN_VERY_HEAVY = 50.0;
    private static final double SNOW_LIGHT = 0.5;
    private static final double SNOW_HEAVY = 5.0;
    private static final double WIND_LIGHT = 4.0;
    private static final double WIND_STRONG = 9.0;

    private WeatherCodeTranslator() {
    }

    static String sky(String code) {
        if (code == null || code.isBlank()) return "";
        return SKY_CODES.getOrDefault(code, code);
    }

    static String precipType(String code, boolean ultra) {
        if (code == null || code.isBlank()) return "";
        Map<String, String> table = ultra ? PTY_ULTRA : PTY_VILAGE;
        return table.getOrDefault(code, code);
    }

    static String rain(String raw) {
        if (raw == null || NO_DATA.contains(raw.toLowerCase())) return "강수없음";
        try {
            double f = Double.parseDouble(raw);
            if (f < RAIN_LIGHT) return "1mm 미만";
            if (f < RAIN_HEAVY) return raw + "mm";
            if (f < RAIN_VERY_HEAVY) return "30.0~50.0mm";
            return "50.0mm 이상";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    static String snow(String raw) {
        if (raw == null || NO_DATA.contains(raw.toLowerCase())) return "적설없음";
        try {
            double f = Double.parseDouble(raw);
            if (f < SNOW_LIGHT) return "0.5cm 미만";
            if (f < SNOW_HEAVY) return raw + "cm";
            return "5.0cm 이상";
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    static String windDirection(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            int deg = (int) Double.parseDouble(raw);
            int idx = ((int) ((deg + 22.5 * 0.5) / 22.5) % 16 + 16) % 16;
            return WIND_DIRECTIONS[idx];
        } catch (NumberFormatException e) {
            return "";
        }
    }

    static String windSpeed(String raw) {
        if (raw == null || raw.isBlank()) return "";
        try {
            double f = Double.parseDouble(raw);
            if (f < WIND_LIGHT) return "약한 바람";
            if (f < WIND_STRONG) return "약간 강한 바람";
            return "강한 바람";
        } catch (NumberFormatException e) {
            return switch (raw) {
                case "1" -> "약한 바람";
                case "2" -> "약간 강한 바람";
                case "3" -> "강한 바람";
                default -> raw;
            };
        }
    }
}
