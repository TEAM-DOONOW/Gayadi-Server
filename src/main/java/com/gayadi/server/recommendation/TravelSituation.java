package com.gayadi.server.recommendation;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/** 추천과 상황 대처 Agent가 공유하는 현재 여행 상황 값입니다. */
public record TravelSituation(
        @Valid Weather weather,
        @Valid Congestion congestion,
        @Valid Transit transit
) {

    private static final int INDOOR_PRECIPITATION_PROBABILITY = 70;
    private static final int OUTDOOR_PRECAUTION_PROBABILITY = 40;
    private static final double HEAVY_PRECIPITATION_MM = 10.0;
    private static final double STRONG_WIND_MPS = 10.0;
    private static final int HIGH_OCCUPANCY_PERCENT = 80;
    private static final int TRANSIT_DISRUPTION_DELAY_MINUTES = 15;

    public TravelSituation {
        weather = weather == null ? Weather.empty() : weather;
        congestion = congestion == null ? Congestion.empty() : congestion;
        transit = transit == null ? Transit.empty() : transit;
    }

    public static TravelSituation empty() {
        return new TravelSituation(Weather.empty(), Congestion.empty(), Transit.empty());
    }

    public Policy policy() {
        boolean indoorRequired = weather.rainOrSnow()
                || weather.precipitationProbability() != null
                && weather.precipitationProbability() >= INDOOR_PRECIPITATION_PROBABILITY
                || weather.precipitationMm() != null
                && weather.precipitationMm() >= HEAVY_PRECIPITATION_MM;
        boolean avoidOutdoor = indoorRequired
                || weather.precipitationProbability() != null
                && weather.precipitationProbability() >= OUTDOOR_PRECAUTION_PROBABILITY
                || weather.windSpeedMps() != null
                && weather.windSpeedMps() >= STRONG_WIND_MPS;
        boolean avoidCrowded = congestion.high()
                || congestion.occupancyPercent() != null
                && congestion.occupancyPercent() >= HIGH_OCCUPANCY_PERCENT;
        boolean transitDisrupted = transit.missed()
                || transit.delayMinutes() != null
                && transit.delayMinutes() >= TRANSIT_DISRUPTION_DELAY_MINUTES;

        StringBuilder summary = new StringBuilder();
        if (indoorRequired) summary.append("실내 장소 우선");
        else if (avoidOutdoor) summary.append("야외 활동 축소");
        if (avoidCrowded) append(summary, "혼잡 장소 회피");
        if (transitDisrupted) append(summary, "대중교통 대체 경로 필요");
        if (summary.isEmpty()) summary.append("특이 상황 없음");
        return new Policy(indoorRequired, avoidOutdoor, avoidCrowded, transitDisrupted,
                summary.toString());
    }

    private static void append(StringBuilder target, String value) {
        if (!target.isEmpty()) target.append(", ");
        target.append(value);
    }

    public record Policy(
            boolean indoorRequired,
            boolean avoidOutdoor,
            boolean avoidCrowded,
            boolean transitDisrupted,
            String summary
    ) {
    }

    public record Weather(
            @Size(max = 30) String condition,
            @Size(max = 30) String precipitationType,
            @DecimalMin("0.0") @DecimalMax("1000.0") Double precipitationMm,
            @Min(0) @Max(100) Integer precipitationProbability,
            @DecimalMin("-100.0") @DecimalMax("100.0") Double temperatureC,
            @DecimalMin("0.0") @DecimalMax("150.0") Double windSpeedMps
    ) {

        public Weather {
            condition = normalize(condition);
            precipitationType = normalize(precipitationType);
        }

        public static Weather empty() {
            return new Weather("", "", null, null, null, null);
        }

        public boolean isEmpty() {
            return condition.isBlank()
                    && precipitationType.isBlank()
                    && precipitationMm == null
                    && precipitationProbability == null
                    && temperatureC == null
                    && windSpeedMps == null;
        }

        private boolean rainOrSnow() {
            return containsAny(condition, "RAIN", "SNOW", "SHOWER", "비", "눈", "소나기")
                    || containsAny(precipitationType, "RAIN", "SNOW", "SHOWER", "비", "눈", "소나기");
        }
    }

    public record Congestion(
            @Size(max = 30) String level,
            @Min(0) @Max(100) Integer occupancyPercent,
            @Size(max = 100) String area
    ) {

        public Congestion {
            level = normalize(level);
            area = normalize(area);
        }

        public static Congestion empty() {
            return new Congestion("", null, "");
        }

        public boolean isEmpty() {
            return level.isBlank() && occupancyPercent == null && area.isBlank();
        }

        private boolean high() {
            return containsAny(level, "HIGH", "CRITICAL", "심각", "높음", "매우혼잡", "혼잡");
        }
    }

    public record Transit(
            boolean missed,
            @Size(max = 30) String mode,
            @Min(0) @Max(1440) Integer delayMinutes,
            @Size(max = 500) String reason,
            @Size(max = 40) String nextDepartureAt
    ) {

        public Transit {
            mode = normalize(mode);
            reason = normalize(reason);
            nextDepartureAt = normalize(nextDepartureAt);
        }

        public static Transit empty() {
            return new Transit(false, "", null, "", "");
        }

        @AssertTrue(message = "다음 출발 시각은 UTC 오프셋을 포함한 ISO-8601 형식이어야 합니다.")
        public boolean isNextDepartureAtValid() {
            if (nextDepartureAt.isBlank()) return true;
            try {
                OffsetDateTime.parse(nextDepartureAt);
                return true;
            } catch (DateTimeParseException exception) {
                return false;
            }
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean containsAny(String value, String... terms) {
        String normalized = normalize(value).toUpperCase(Locale.ROOT);
        for (String term : terms) {
            if (normalized.contains(term.toUpperCase(Locale.ROOT))) return true;
        }
        return false;
    }
}
