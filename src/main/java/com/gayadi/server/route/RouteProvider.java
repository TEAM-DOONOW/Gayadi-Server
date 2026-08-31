package com.gayadi.server.route;

import com.gayadi.server.common.Location;

import java.util.List;

/** 장소 구간별 이동 시간과 비용을 계산하는 경로 공급자 계약입니다. */
public interface RouteProvider {

    String LOCAL_ESTIMATE = "LOCAL_ESTIMATE";
    String TMAP_TRANSIT = "TMAP_TRANSIT";

    String providerName();

    /**
     * 정류장 순서 전체를 한 번에 계산한다. 외부 공급자는 구간마다 HTTP 요청을 반복하지 않고
     * 경유지 일괄 API를 사용해야 한다.
     */
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
