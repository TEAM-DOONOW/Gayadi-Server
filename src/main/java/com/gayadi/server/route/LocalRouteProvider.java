package com.gayadi.server.route;

import com.gayadi.server.common.Location;
import org.springframework.context.annotation.Profile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 외부 연동 없이 직선거리로 예상 경로를 계산하는 기본 공급자입니다. */
@Component
@Profile("!external-route")
@ConditionalOnProperty(name = "route.provider", havingValue = "local", matchIfMissing = true)
public class LocalRouteProvider implements RouteProvider {

    @Override
    public String providerName() {
        return LOCAL_ESTIMATE;
    }

    @Override
    public List<RouteEstimate> estimateSegments(List<Location> stops, String phase) {
        List<RouteEstimate> estimates = new ArrayList<>();
        for (int index = 0; index < stops.size() - 1; index++) {
            estimates.add(estimate(stops.get(index), stops.get(index + 1)));
        }
        return List.copyOf(estimates);
    }

    private RouteEstimate estimate(Location origin, Location destination) {
        double lat = origin.latitude() - destination.latitude();
        double lng = origin.longitude() - destination.longitude();
        double straightKm = Math.sqrt(lat * lat + lng * lng) * 88.0;
        int duration = Math.max(12, (int) Math.round(straightKm * 4.2 + 10));
        int transfers = duration > 45 ? 2 : duration > 25 ? 1 : 0;
        int fare = 1500 + transfers * 250;
        return new RouteEstimate(duration, transfers, fare,
                "직선거리를 바탕으로 계산한 대중교통 예상 경로입니다. 실제 교통 정보와 다를 수 있습니다.",
                providerName());
    }
}
