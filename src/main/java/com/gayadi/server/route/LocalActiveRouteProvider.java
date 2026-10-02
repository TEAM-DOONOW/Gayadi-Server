package com.gayadi.server.route;

import com.gayadi.server.common.Location;
import java.util.ArrayList;
import java.util.List;

/** 직선거리로 도보·자전거 시간을 추정하며 실제 도로 연결 여부는 판단하지 않습니다. */
public class LocalActiveRouteProvider implements RouteProvider {
    private final TransportMode mode;

    public LocalActiveRouteProvider(TransportMode mode) {
        if (mode != TransportMode.WALK && mode != TransportMode.BICYCLE) {
            throw new IllegalArgumentException("도보 또는 자전거만 지원합니다.");
        }
        this.mode = mode;
    }

    @Override
    public String providerName() {
        return LOCAL_ESTIMATE;
    }

    @Override
    public TransportMode transportMode() {
        return mode;
    }

    @Override
    public List<RouteEstimate> estimateSegments(List<Location> stops, String phase) {
        List<RouteEstimate> estimates = new ArrayList<>();
        for (int i = 1; i < stops.size(); i++) {
            String summary = mode == TransportMode.WALK
                    ? "직선거리 기반 도보 예상 구간입니다."
                    : "직선거리 기반 자전거 예상 구간입니다. 대여료는 제외됩니다.";
            estimates.add(new RouteEstimate(durationMinutes(stops.get(i - 1), stops.get(i), mode),
                    0, 0, summary, providerName()));
        }
        return List.copyOf(estimates);
    }

    static int durationMinutes(Location origin, Location destination, TransportMode mode) {
        double lat1 = Math.toRadians(origin.latitude());
        double lat2 = Math.toRadians(destination.latitude());
        double deltaLat = lat2 - lat1;
        double deltaLng = Math.toRadians(destination.longitude() - origin.longitude());
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLng / 2), 2);
        double distanceKm = 6371.0 * 2 * Math.asin(Math.sqrt(Math.min(1.0, a)));
        double speedKmh = mode == TransportMode.WALK ? 4.0 : 15.0;
        return Math.max(1, (int) Math.ceil(distanceKm / speedKmh * 60));
    }
}
