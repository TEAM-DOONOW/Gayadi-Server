package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.Location;
import com.gayadi.server.event.EventService;
import com.gayadi.server.route.RouteService;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.travel.DepartureMode;
import com.gayadi.server.travel.TripService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TravelFlowIntegrationTests {
    @Autowired UserService users;
    @Autowired TripService trips;
    @Autowired SurveyService surveys;
    @Autowired PlanService plans;
    @Autowired RouteService routes;
    @Autowired EventService events;

    @Test
    void completeGroupTravelFlow() {
        String ownerId = id(users.create("여행장"));
        String memberUserId = id(users.create("친구"));
        Location seoulStation = new Location("서울역", 37.5547, 126.9706);

        Map<String, Object> trip = trips.create(new TripService.CreateTrip(
                ownerId,
                "서울 당일치기",
                DepartureMode.GROUP_MEETING,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).minusMinutes(40),
                seoulStation,
                new Location("왕십리역", 37.5614, 127.0372),
                new Location("왕십리역", 37.5614, 127.0372)
        ));
        String tripId = id(trip);
        Map<String, Object> member = trips.addMember(tripId, new TripService.AddMember(
                memberUserId,
                new Location("강남역", 37.4979, 127.0276),
                new Location("강남역", 37.4979, 127.0276)
        ));
        String memberId = id(member);

        surveys.respond(tripId, ownerId, Map.of("pace", 2, "indoor", 5, "food", 3));
        surveys.respond(tripId, memberUserId, Map.of("pace", 3, "indoor", 4, "food", 5));
        Map<String, Object> plan = plans.generate(tripId);
        assertThat(number(plan, "revision_no")).isEqualTo(1);
        assertThat(plan.get("items")).asList().hasSize(3);

        Map<String, Object> memberRoute = routes.recommend(tripId, RouteService.RoutePhase.DEPARTURE, memberId);
        Map<String, Object> groupRoute = routes.recommend(tripId, RouteService.RoutePhase.DEPARTURE, null);
        assertThat(memberRoute.get("scope")).isEqualTo("MEMBER");
        assertThat(groupRoute.get("scope")).isEqualTo("GROUP");

        trips.start(tripId);
        Map<String, Object> proposal = events.observe(tripId, new EventService.Observation(
                "20000000-0000-0000-0000-000000000001", "RAIN", "TEST", EventService.Severity.HIGH,
                Map.of("rainfallMm", 20)
        ));
        String proposalId = id(proposal);
        events.decide(tripId, proposalId, new EventService.Decision(
                true, "INDOOR_SHELTER", 1, ownerId
        ));
        assertThat(number(plans.get(tripId), "revision_no")).isEqualTo(2);

        Map<String, Object> returnRoute = routes.recommend(tripId, RouteService.RoutePhase.RETURN, memberId);
        assertThat(returnRoute.get("phase").toString()).isEqualTo("RETURN");
        assertThat(value(trips.complete(tripId), "status")).isEqualTo("COMPLETED");
    }

    private String id(Map<String, Object> row) {
        return value(row, "id").toString();
    }

    private int number(Map<String, Object> row, String key) {
        return ((Number) value(row, key)).intValue();
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase());
    }
}
