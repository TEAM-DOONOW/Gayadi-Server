package com.gayadi.server.route;

import com.gayadi.server.common.Location;

import java.util.List;

/** 장소 구간별 이동 시간과 비용을 계산하는 경로 공급자 계약입니다. */
public interface RouteProvider {

    String LOCAL_ESTIMATE = "LOCAL_ESTIMATE";
    String TMAP_TRANSIT = "TMAP_TRANSIT";

    String providerName();

    default TransportMode transportMode() {
        return TransportMode.PUBLIC_TRANSIT;
    }

    /** 입력 순서대로 각 구간을 계산합니다. 최적화 중에는 방향별 구간 결과를 재사용합니다. */
    List<RouteEstimate> estimateSegments(List<Location> stops, String phase);

    record RouteEstimate(
            int durationMinutes,
            int transferCount,
            int fare,
            String summary,
            String providerName
    ) {

        public RouteEstimate(int durationMinutes, int transferCount, int fare, String summary) {
            this(durationMinutes, transferCount, fare, summary, "");
        }
    }
}
