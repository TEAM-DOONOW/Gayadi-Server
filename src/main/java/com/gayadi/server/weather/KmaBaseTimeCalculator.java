package com.gayadi.server.weather;

import com.gayadi.server.common.exception.BusinessException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 기상청 단기예보 발표시각 자동 계산.
 * <p>
 * 초단기실황: 매 정시 발표, 10분 후 호출 가능
 * 초단기예보: 매시 30분 발표, 45분 후 호출 가능
 * 단기예보:   1일 8회(02,05,08,11,14,17,20,23시) 발표, 10분 후 호출 가능
 */
final class KmaBaseTimeCalculator {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HHmm");

    private static final int ULTRA_NCST_DELAY_MIN = 10;
    private static final int ULTRA_FCST_AVAILABLE_MIN = 45;
    private static final int ULTRA_FCST_BASE_MIN = 30;
    private static final int VILAGE_FCST_DELAY_MIN = 10;
    private static final List<Integer> VILAGE_BASE_HOURS =
            List.of(2, 5, 8, 11, 14, 17, 20, 23);

    enum ForecastType { ULTRA_NCST, ULTRA_FCST, VILAGE_FCST }

    record BaseDateTime(
            String date,
            String time
    ) {
    }

    private KmaBaseTimeCalculator() {
    }

    /**
     * 명시적 baseDate/baseTime이 있으면 그대로 반환, 없으면 발표 가능한 최신 시각 계산.
     */
    static BaseDateTime resolve(ForecastType type, String baseDate, String baseTime) {
        boolean hasDate = baseDate != null && !baseDate.isBlank();
        boolean hasTime = baseTime != null && !baseTime.isBlank();
        if (hasDate != hasTime) {
            throw new BusinessException(WeatherErrorCode.WEATHER_BASE_PAIR_REQUIRED);
        }
        if (hasDate) {
            validateExplicit(type, baseDate, baseTime);
            return new BaseDateTime(baseDate.trim(), baseTime.trim());
        }
        return latest(type);
    }

    static BaseDateTime latest(ForecastType type) {
        return latest(type, LocalDateTime.now(KST));
    }

    static BaseDateTime latest(ForecastType type, LocalDateTime now) {
        return switch (type) {
            case ULTRA_NCST -> {
                LocalDateTime base = now.minusMinutes(ULTRA_NCST_DELAY_MIN)
                        .withMinute(0).withSecond(0).withNano(0);
                yield of(base);
            }
            case ULTRA_FCST -> {
                LocalDateTime base;
                if (now.getMinute() >= ULTRA_FCST_AVAILABLE_MIN) {
                    base = now.withMinute(ULTRA_FCST_BASE_MIN).withSecond(0).withNano(0);
                } else {
                    base = now.minusHours(1).withMinute(ULTRA_FCST_BASE_MIN).withSecond(0).withNano(0);
                }
                yield of(base);
            }
            case VILAGE_FCST -> {
                LocalDateTime available = now.minusMinutes(VILAGE_FCST_DELAY_MIN);
                int hour = available.getHour();
                int baseHour = -1;
                for (int h : VILAGE_BASE_HOURS) {
                    if (h <= hour) {
                        baseHour = h;
                    }
                }
                LocalDateTime base = (baseHour == -1)
                        ? available.minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0)
                        : available.withHour(baseHour).withMinute(0).withSecond(0).withNano(0);
                yield of(base);
            }
        };
    }

    private static void validateExplicit(
            ForecastType type, String rawDate, String rawTime) {
        String date = rawDate.trim();
        String time = rawTime.trim();
        if (!date.matches("\\d{8}") || !time.matches("\\d{4}")) {
            throw invalidBaseDateTime();
        }
        int hour = Integer.parseInt(time.substring(0, 2));
        int minute = Integer.parseInt(time.substring(2));
        if (hour > 23 || minute > 59) {
            throw invalidBaseDateTime();
        }
        try {
            LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE);
            LocalTime.parse(time, TIME_FMT);
        } catch (DateTimeParseException exception) {
            throw invalidBaseDateTime();
        }
        boolean allowed = switch (type) {
            case ULTRA_NCST -> minute == 0;
            case ULTRA_FCST -> minute == ULTRA_FCST_BASE_MIN;
            case VILAGE_FCST -> minute == 0 && VILAGE_BASE_HOURS.contains(hour);
        };
        if (!allowed) {
            throw new BusinessException(WeatherErrorCode.WEATHER_BASE_TIME_UNAVAILABLE);
        }
    }

    private static BusinessException invalidBaseDateTime() {
        return new BusinessException(WeatherErrorCode.WEATHER_BASE_DATETIME_INVALID);
    }

    private static BaseDateTime of(LocalDateTime base) {
        return new BaseDateTime(base.format(DATE_FMT), base.format(TIME_FMT));
    }
}
