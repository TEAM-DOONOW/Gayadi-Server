package com.gayadi.server.route;

import com.gayadi.server.common.Location;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!external-route")
public class LocalRouteProvider implements RouteProvider {
    @Override
    public RouteEstimate estimate(Location origin, Location destination, String phase) {
        double lat = origin.latitude() - destination.latitude();
        double lng = origin.longitude() - destination.longitude();
        double straightKm = Math.sqrt(lat * lat + lng * lng) * 88.0;
        int duration = Math.max(12, (int) Math.round(straightKm * 4.2 + 10));
        int transfers = duration > 45 ? 2 : duration > 25 ? 1 : 0;
        int fare = 1_500 + transfers * 250;
        return new RouteEstimate(duration, transfers, fare,
                "LOCAL_STUB: 대중교통 우선 예상 경로 (외부 경로 API 연동 전)");
    }
}
