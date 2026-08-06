package com.gayadi.server.route;

import com.gayadi.server.common.Location;

public interface RouteProvider {

    RouteEstimate estimate(Location origin, Location destination, String phase);

    record RouteEstimate(int durationMinutes, int transferCount, int fare, String summary) {
    }
}
