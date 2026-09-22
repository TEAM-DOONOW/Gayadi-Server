package com.gayadi.server.route;

import java.time.OffsetDateTime;
import java.time.ZoneId;

/** 모든 후보에 동일한 출발 시각과 선택 기준을 적용합니다. */
public record TransitRoutingOptions(
        OffsetDateTime departureAt, TransitPreference preference, int stopoverMinutes
) {
    public TransitRoutingOptions {
        if (departureAt == null) departureAt = OffsetDateTime.now(ZoneId.of("Asia/Seoul"));
        if (preference == null) preference = TransitPreference.FASTEST;
        if (stopoverMinutes < 0 || stopoverMinutes > 1440) {
            throw new IllegalArgumentException("체류시간은 0~1440분이어야 합니다.");
        }
    }

    public static TransitRoutingOptions defaults() {
        return new TransitRoutingOptions(null, TransitPreference.FASTEST, 0);
    }
}
