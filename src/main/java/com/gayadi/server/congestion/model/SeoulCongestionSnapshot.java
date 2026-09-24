package com.gayadi.server.congestion.model;

import java.time.OffsetDateTime;
import java.util.List;

/** 서울시 지정 핫스팟의 실시간 인구와 시간대별 예측을 표현합니다. */
public record SeoulCongestionSnapshot(
        String areaName,
        String level,
        Integer populationMin,
        Integer populationMax,
        OffsetDateTime observedAt,
        List<Hourly> hourly
) {

    /** 서울시가 제공하는 한 시간대의 예상 인구와 혼잡 단계입니다. */
    public record Hourly(
            int hour,
            String level,
            Integer populationMin,
            Integer populationMax
    ) {

        public double averagePopulation() {
            if (populationMin == null && populationMax == null) {
                return 0;
            }
            if (populationMin == null) {
                return populationMax;
            }
            if (populationMax == null) {
                return populationMin;
            }
            return (populationMin + populationMax) / 2.0;
        }
    }
}
