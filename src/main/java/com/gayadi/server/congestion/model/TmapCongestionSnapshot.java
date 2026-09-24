package com.gayadi.server.congestion.model;

import java.time.OffsetDateTime;
import java.util.List;

/** TMAP 장소 혼잡도 API의 실시간 값과 요일별 시간대 통계를 전달합니다. */
public record TmapCongestionSnapshot(
        String poiId,
        String poiName,
        Realtime realtime,
        List<Hourly> hourly
) {

    /** 가장 최근 한 시간 동안 집계된 장소 혼잡도입니다. */
    public record Realtime(
            double densityPerSquareMeter,
            int providerLevel,
            String level,
            OffsetDateTime observedAt
    ) {
    }

    /** 최근 30일의 같은 요일을 기준으로 집계한 시간대별 평균 혼잡도입니다. */
    public record Hourly(
            int hour,
            double densityPerSquareMeter,
            int providerLevel,
            String level
    ) {
    }
}
