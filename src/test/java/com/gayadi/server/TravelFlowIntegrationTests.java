package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.event.EventService;
import com.gayadi.server.event.Severity;
import com.gayadi.server.route.RoutePhase;
import com.gayadi.server.route.RouteService;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.survey.SurveyController;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.travel.DepartureMode;
import com.gayadi.server.travel.TripService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

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
        long ownerId = id(users.create("여행장"));
        long memberUserId = id(users.create("친구"));

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        Map<String, Object> trip = trips.create(new TripService.CreateTrip(
                ownerId,
                "서울 당일치기",
                tomorrow,
                tomorrow,
                DepartureMode.TOGETHER,
                tomorrow.atTime(9, 0).minusMinutes(40),
                1L,
                1L,
                null,
                null,
                2L,
                2L
        ));
        long tripId = id(trip);

        Map<String, Object> member = trips.addMember(tripId, new TripService.AddMember(
                memberUserId,
                3L,
                3L
        ));
        long memberId = id(member);

        surveys.respond(tripId, ownerId, List.of(
                item(1L, 1L),
                item(2L, 3L),
                item(3L, 5L)
        ));
        surveys.respond(tripId, memberUserId, List.of(
                item(1L, 1L),
                item(2L, 3L),
                item(3L, 5L)
        ));

        Map<String, Object> plan = plans.generate(tripId);
        Assertions.assertThat(RowSupport.intValue(plan, "version")).isEqualTo(0);
        Assertions.assertThat((List<?>) plan.get("items")).hasSize(3);

        Map<String, Object> memberRoute = routes.recommend(tripId, RoutePhase.DEPARTURE, memberId);
        Map<String, Object> groupRoute = routes.recommend(tripId, RoutePhase.DEPARTURE, null);
        Assertions.assertThat(memberRoute.get("scope")).isEqualTo("MEMBER");
        Assertions.assertThat(groupRoute.get("scope")).isEqualTo("GROUP");

        trips.start(tripId);

        Map<String, Object> proposal = events.observe(tripId, new EventService.Observation(
                1L, "WEATHER", "TEST", Severity.HIGH,
                Map.of("rainfallMm", 20)
        ));
        long proposalId = id(proposal);

        events.decide(tripId, proposalId, new EventService.Decision(
                true, "INDOOR_SHELTER", 0, ownerId
        ));
        Assertions.assertThat(RowSupport.intValue(plans.get(tripId), "version")).isEqualTo(1);

        Map<String, Object> returnRoute = routes.recommend(tripId, RoutePhase.RETURN, memberId);
        Assertions.assertThat(returnRoute.get("phase").toString()).isEqualTo("RETURN");

        Assertions.assertThat(RowSupport.strValue(trips.complete(tripId), "status"))
                .isEqualTo("COMPLETED");
    }

    private long id(Map<String, Object> row) {
        return RowSupport.longValue(row, "id");
    }

    private SurveyController.ResponseItem item(long questionId, long optionId) {
        SurveyController.ResponseItem item = new SurveyController.ResponseItem();
        item.setQuestionId(questionId);
        item.setOptionId(optionId);
        return item;
    }
}
