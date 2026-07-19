package com.gayadi.server.route;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.Location;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.travel.DepartureMode;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class RouteService {
    private final JdbcClient jdbc;
    private final TripService trips;
    private final PlanService plans;
    private final RouteProvider provider;
    private final ObjectMapper objectMapper;
    private final JsonSupport json;

    public RouteService(JdbcClient jdbc, TripService trips, PlanService plans, RouteProvider provider,
                        ObjectMapper objectMapper, JsonSupport json) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.plans = plans;
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.json = json;
    }

    public Map<String, Object> recommend(String tripId, RoutePhase phase, String memberId) {
        Map<String, Object> trip = trips.requireTrip(tripId);
        Map<String, Object> member = memberId == null ? null : member(tripId, memberId);
        RouteContext context = switch (phase) {
            case DEPARTURE -> departureContext(trip, member);
            case RETURN -> returnContext(tripId, member);
            case BETWEEN_PLACES -> throw new ApiException(HttpStatus.NOT_IMPLEMENTED,
                    "장소 간 경로는 일정 변경 흐름에서 계산됩니다.");
        };
        RouteProvider.RouteEstimate estimate = provider.estimate(context.origin(), context.destination(), phase.name());
        String id = UUID.randomUUID().toString();
        Map<String, Object> routeData = Map.of("provider", "LOCAL_STUB", "summary", estimate.summary());
        jdbc.sql("""
                INSERT INTO trip_routes(id, trip_id, member_id, scope, phase, origin, destination,
                                        duration_minutes, transfer_count, fare, route_data, valid_until)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """).params(id, tripId, memberId, context.scope(), phase.name(), json.write(context.origin()),
                        json.write(context.destination()), estimate.durationMinutes(), estimate.transferCount(),
                        estimate.fare(), json.write(routeData), LocalDateTime.now().plusMinutes(10)).update();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("tripId", tripId);
        result.put("memberId", memberId);
        result.put("scope", context.scope());
        result.put("phase", phase);
        result.put("origin", context.origin());
        result.put("destination", context.destination());
        result.put("durationMinutes", estimate.durationMinutes());
        result.put("transferCount", estimate.transferCount());
        result.put("fare", estimate.fare());
        result.put("validUntil", LocalDateTime.now().plusMinutes(10));
        result.put("provider", "LOCAL_STUB");
        return result;
    }

    private RouteContext departureContext(Map<String, Object> trip, Map<String, Object> member) {
        DepartureMode mode = DepartureMode.valueOf(PlanService.value(trip, "departure_mode").toString());
        Location firstPlace = placeLocation(plans.firstPlace(PlanService.value(trip, "id").toString()));
        if (mode == DepartureMode.GROUP_MEETING && member == null) {
            return new RouteContext(readLocation(PlanService.value(trip, "meeting_location")), firstPlace, "GROUP");
        }
        if (member == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "개별 출발 경로에는 memberId가 필요합니다.");
        }
        Location origin = readLocation(PlanService.value(member, "departure_location"));
        Location destination = mode == DepartureMode.GROUP_MEETING
                ? readLocation(PlanService.value(trip, "meeting_location")) : firstPlace;
        return new RouteContext(origin, destination, "MEMBER");
    }

    private RouteContext returnContext(String tripId, Map<String, Object> member) {
        if (member == null) throw new ApiException(HttpStatus.BAD_REQUEST, "귀가 경로에는 memberId가 필요합니다.");
        return new RouteContext(placeLocation(plans.lastPlace(tripId)),
                readLocation(PlanService.value(member, "return_destination")), "MEMBER");
    }

    private Map<String, Object> member(String tripId, String memberId) {
        return jdbc.sql("SELECT * FROM trip_members WHERE trip_id = ? AND id = ?")
                .params(tripId, memberId).query().listOfRows().stream().findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행 멤버를 찾을 수 없습니다."));
    }

    private Location placeLocation(Map<String, Object> place) {
        return new Location(PlanService.value(place, "name").toString(),
                ((Number) PlanService.value(place, "latitude")).doubleValue(),
                ((Number) PlanService.value(place, "longitude")).doubleValue());
    }

    private Location readLocation(Object raw) {
        try {
            return objectMapper.readValue(raw.toString(), Location.class);
        } catch (JacksonException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 위치 정보를 읽을 수 없습니다.");
        }
    }

    public enum RoutePhase { DEPARTURE, BETWEEN_PLACES, RETURN }

    private record RouteContext(Location origin, Location destination, String scope) {
    }
}
