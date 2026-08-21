package com.gayadi.server.weather;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
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

    record BaseDateTime(String date, String time) {
    }

    private KmaBaseTimeCalculator() {
    }

    /**
     * 명시적 baseDate/baseTime이 있으면 그대로 반환, 없으면 발표 가능한 최신 시각 계산.
     */
    static BaseDateTime resolve(ForecastType type, String baseDate, String baseTime) {
        if (baseDate != null && !baseDate.isBlank()
                && baseTime != null && !baseTime.isBlank()) {
            return new BaseDateTime(baseDate, baseTime);
        }
        return latest(type);
    }

    static BaseDateTime latest(ForecastType type) {
        LocalDateTime now = LocalDateTime.now(KST);
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
                    if (h <= hour) baseHour = h;
                }
                LocalDateTime base = (baseHour == -1)
                        ? available.minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0)
                        : available.withHour(baseHour).withMinute(0).withSecond(0).withNano(0);
                yield of(base);
            }
        };
    }

    private static BaseDateTime of(LocalDateTime base) {
        return new BaseDateTime(base.format(DATE_FMT), base.format(TIME_FMT));
    }
}
