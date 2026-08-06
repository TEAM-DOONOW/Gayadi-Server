package com.gayadi.server.route;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.Location;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.travel.DepartureMode;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RouteService {

    private final JdbcClient jdbc;
    private final TripService trips;
    private final PlanService plans;
    private final RouteProvider provider;
    private final JsonSupport json;
    private final KeyHelper keyHelper;

    public RouteService(JdbcClient jdbc, TripService trips, PlanService plans,
                         RouteProvider provider, JsonSupport json, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.plans = plans;
        this.provider = provider;
        this.json = json;
        this.keyHelper = keyHelper;
    }

    public Map<String, Object> recommend(long tripId, RoutePhase phase, Long memberId) {
        Map<String, Object> trip = trips.requireTrip(tripId);
        Map<String, Object> member = memberId != null ? member(tripId, memberId) : null;

        RouteContext context = switch (phase) {
            case DEPARTURE -> departureContext(trip, member);
            case RETURN -> returnContext(tripId, member);
            case IN_TRIP -> throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                    "장소 간 경로는 일정 변경 흐름에서 계산됩니다.");
        };

        RouteProvider.RouteEstimate estimate = provider.estimate(
                context.origin(), context.destination(), phase.name());

        long planId = getPlanId(tripId);
        Map<String, Object> routeData = Map.of("provider", "LOCAL_STUB", "summary", estimate.summary());

        long routeId = keyHelper.insert("""
                INSERT INTO travel_routes (plan_id, member_id, phase, route_data, transport_mode,
                                            duration_minutes, transfer_count, fare, status, recommended_at)
                VALUES (?, ?, ?, ?, 'PUBLIC_TRANSIT', ?, ?, ?, 'RECOMMENDED', CURRENT_TIMESTAMP)
                """,
                planId, memberId, phase.name(), json.write(routeData),
                estimate.durationMinutes(), estimate.transferCount(), estimate.fare());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", routeId);
        result.put("tripId", tripId);
        result.put("planId", planId);
        result.put("memberId", memberId);
        result.put("scope", context.scope());
        result.put("phase", phase.name());
        result.put("origin", context.origin());
        result.put("destination", context.destination());
        result.put("durationMinutes", estimate.durationMinutes());
        result.put("transferCount", estimate.transferCount());
        result.put("fare", estimate.fare());
        result.put("transportMode", "PUBLIC_TRANSIT");
        result.put("status", "RECOMMENDED");
        result.put("provider", "LOCAL_STUB");
        return result;
    }

    private RouteContext departureContext(Map<String, Object> trip, Map<String, Object> member) {
        DepartureMode mode = DepartureMode.valueOf(RowSupport.strValue(trip, "departure_mode"));
        long tripId = RowSupport.longValue(trip, "id");
        Location firstPlace = placeLocation(plans.firstPlace(tripId));

        if (mode == DepartureMode.TOGETHER && member == null) {
            Long meetingPlaceId = nullableLong(trip, "meeting_place_id");
            if (meetingPlaceId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "집결 장소가 설정되지 않았습니다.");
            }
            return new RouteContext(placeLocation(getPlace(meetingPlaceId)), firstPlace, "GROUP");
        }
        if (member == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "개별 출발 경로에는 memberId가 필요합니다.");
        }
        Long departurePlaceId = nullableLong(member, "departure_place_id");
        if (departurePlaceId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "출발 장소가 설정되지 않았습니다.");
        }
        Location origin = placeLocation(getPlace(departurePlaceId));
        Location destination = mode == DepartureMode.TOGETHER
                ? placeLocation(getPlace(nullableLong(trip, "meeting_place_id")))
                : firstPlace;
        return new RouteContext(origin, destination, "MEMBER");
    }

    private RouteContext returnContext(long tripId, Map<String, Object> member) {
        if (member == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "귀가 경로에는 memberId가 필요합니다.");
        }
        Long returnPlaceId = nullableLong(member, "return_place_id");
        if (returnPlaceId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "귀가 장소가 설정되지 않았습니다.");
        }
        return new RouteContext(
                placeLocation(plans.lastPlace(tripId)),
                placeLocation(getPlace(returnPlaceId)),
                "MEMBER"
        );
    }

    private long getPlanId(long tripId) {
        return jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ? ORDER BY day_number LIMIT 1")
                .param(tripId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "경로 계산 전에 일정이 필요합니다."));
    }

    private Map<String, Object> member(long tripId, long memberId) {
        return jdbc.sql("SELECT * FROM trip_participants WHERE trip_id = ? AND id = ?")
                .params(tripId, memberId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행 멤버를 찾을 수 없습니다."));
    }

    private Map<String, Object> getPlace(long placeId) {
        return jdbc.sql("SELECT * FROM places WHERE id = ? AND status != 'DELETED'")
                .param(placeId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다."));
    }

    private Location placeLocation(Map<String, Object> place) {
        return new Location(
                RowSupport.strValue(place, "name"),
                ((Number) RowSupport.value(place, "latitude")).doubleValue(),
                ((Number) RowSupport.value(place, "longitude")).doubleValue()
        );
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) v = row.get(key.toUpperCase());
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        return Long.parseLong(v.toString());
    }

    private record RouteContext(Location origin, Location destination, String scope) {
    }
}
