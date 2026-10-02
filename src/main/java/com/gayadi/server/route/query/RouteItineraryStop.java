package com.gayadi.server.route.query;

import com.gayadi.server.common.Location;

/** 날짜·좌표 없는 일정·고정 일정 경계를 보존하기 위한 경유지입니다. */
public record RouteItineraryStop(Location location, long planId, long block, boolean fixed) {
}
