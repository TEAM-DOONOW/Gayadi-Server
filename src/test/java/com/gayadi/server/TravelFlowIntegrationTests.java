package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.event.EventService;
import com.gayadi.server.event.dto.response.ChangeProposalResponse;
import com.gayadi.server.event.command.ChangeProposalDecision;
import com.gayadi.server.event.command.EventObservationCommand;
import com.gayadi.server.event.model.EventType;
import com.gayadi.server.event.model.Severity;
import com.gayadi.server.route.RoutePhase;
import com.gayadi.server.route.RouteService;
import com.gayadi.server.route.dto.response.RouteResponse;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.survey.dto.request.SurveyResponseItem;
import com.gayadi.server.travel.model.DepartureMode;
import com.gayadi.server.travel.dto.response.ParticipantResponse;
import com.gayadi.server.travel.dto.response.TripResponse;
import com.gayadi.server.travel.TripService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
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
    @Autowired JdbcClient jdbc;

    @Test
    void completeGroupTravelFlow() {
        long ownerId = users.create("여행장").id();
        long memberUserId = users.create("친구").id();

        LocalDate tomorrow = LocalDate.now().plusDays(1);

        TripResponse trip = trips.create(new TripService.CreateTrip(
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
        long tripId = trip.id();

        ParticipantResponse member = trips.addMember(tripId, new TripService.AddMember(
                memberUserId,
                3L,
                3L
        ));
        long memberId = member.participantId();

        surveys.respond(tripId, ownerId, List.of(
                item("q01", "a"), item("q02", "a"), item("q03", "a"),
                item("q04", "a"), item("q05", "a"), item("q06", "a"),
                item("q07", "a"), item("q08", "a"), item("q09", "a")
        ));
        surveys.respond(tripId, memberUserId, List.of(
                item("q01", "a"), item("q02", "a"), item("q03", "a"),
                item("q04", "a"), item("q05", "a"), item("q06", "a"),
                item("q07", "a"), item("q08", "a"), item("q09", "a")
        ));

        var plan = plans.generate(tripId);
        Assertions.assertThat(plan.version()).isZero();
        Assertions.assertThat(plan.items()).hasSize(3);

        RouteResponse memberRoute = routes.recommend(tripId, RoutePhase.DEPARTURE, memberId);
        RouteResponse groupRoute = routes.recommend(tripId, RoutePhase.DEPARTURE, null);
        Assertions.assertThat(memberRoute.scope()).isEqualTo("MEMBER");
        Assertions.assertThat(groupRoute.scope()).isEqualTo("GROUP");

        RouteResponse itineraryRoute = routes.recommend(tripId, RoutePhase.IN_TRIP, null);
        Assertions.assertThat(itineraryRoute.type()).isEqualTo("ITINERARY");
        Assertions.assertThat(itineraryRoute.scope()).isEqualTo("GROUP");

        trips.start(tripId);

        ChangeProposalResponse proposal = (ChangeProposalResponse) events.observe(
                tripId,
                new EventObservationCommand(
                1L, EventType.WEATHER, "TEST", Severity.HIGH,
                Map.of("rainfallMm", 20)
        ));
        long proposalId = proposal.id();

        events.decide(tripId, proposalId, new ChangeProposalDecision(
                true, "INDOOR_SHELTER", 0, ownerId
        ));
        Integer version = jdbc.sql("SELECT version FROM travel_plans WHERE trip_id = ?")
                .param(tripId).query(Integer.class).single();
        Assertions.assertThat(version).isEqualTo(1);

        RouteResponse returnRoute = routes.recommend(tripId, RoutePhase.RETURN, memberId);
        Assertions.assertThat(returnRoute.phase()).isEqualTo("RETURN");

        Assertions.assertThat(trips.complete(tripId).status())
                .isEqualTo("COMPLETED");
    }

    private SurveyResponseItem item(String questionId, String optionId) {
        SurveyResponseItem item = new SurveyResponseItem();
        item.setQuestionId(questionId);
        item.setOptionId(optionId);
        return item;
    }
}
