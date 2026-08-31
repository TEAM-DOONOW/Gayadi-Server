package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.route.RoutePhase;
import com.gayadi.server.route.RouteService;
import com.gayadi.server.route.dto.request.RouteRecommendationRequest;
import com.gayadi.server.route.dto.response.RouteResponse;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.schedule.model.ScheduleType;
import com.gayadi.server.schedule.dto.response.ScheduleResponse;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.survey.dto.request.SurveyResponseItem;
import com.gayadi.server.travel.model.DepartureMode;
import com.gayadi.server.travel.TripService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SpringBootTest
class RouteScheduleContractIntegrationTests {

    @Autowired UserService users;
    @Autowired TripService trips;
    @Autowired SurveyService surveys;
    @Autowired PlanService plans;
    @Autowired RouteService routes;
    @Autowired ScheduleItemService schedules;
    @Autowired JdbcClient jdbc;

    @Test
    void itineraryContainsEveryPlaceSegmentAndTwoStoredOptions() {
        Assertions.assertThat(Arrays.stream(
                        RouteRecommendationRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("type", "userId");
        Fixture fixture = fixture("전체동선");

        RouteResponse recommendation = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null);

        Assertions.assertThat(recommendation.options())
                .extracting(RouteResponse::optionId)
                .containsExactly("balanced", "crowd");
        Assertions.assertThat(recommendation.optionId()).isEqualTo("balanced");

        List<?> stops = recommendation.stops();
        var segments = recommendation.segments();
        Assertions.assertThat(stops).hasSize(3);
        Assertions.assertThat(segments).hasSize(stops.size() - 1);
        Assertions.assertThat(segments)
                .extracting(segment -> segment.order())
                .containsExactly(1, 2);
        Assertions.assertThat(activeRoutes(fixture.tripId(), RoutePhase.IN_TRIP))
                .isEqualTo(2L);

        RouteResponse home = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.RETURN, fixture.ownerId());
        Assertions.assertThat(home.options())
                .extracting(RouteResponse::optionId)
                .containsExactly("home-fast", "home-rest");
        long participantId = participantId(fixture.tripId(), fixture.ownerId());
        Assertions.assertThat(home.memberId()).isEqualTo(fixture.ownerId());
        Assertions.assertThat(home.userId()).isEqualTo(fixture.ownerId());
        Assertions.assertThat(home.participantId()).isEqualTo(participantId);

        RouteResponse selected = routes.selectForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.RETURN, id(home));
        Assertions.assertThat(selected.memberId()).isEqualTo(fixture.ownerId());
        Assertions.assertThat(selected.participantId()).isEqualTo(participantId);

        RouteResponse selectedByAndroidOption = routes.selectForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.RETURN,
                null, "home-rest", fixture.ownerId());
        Assertions.assertThat(selectedByAndroidOption.optionId()).isEqualTo("home-rest");

        long otherUserId = users.create("다른사용자" + System.nanoTime() % 1_000_000).id();
        Assertions.assertThatThrownBy(() -> routes.recommendForUser(
                        fixture.tripId(), fixture.ownerId(), RoutePhase.RETURN, otherUserId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode().status().value())
                                .isEqualTo(403));
    }

    @Test
    void everyScheduleMutationExpiresRecommendedAndSelectedRoutes() {
        Fixture fixture = fixture("경로만료");
        RouteResponse firstRecommendation = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null);
        routes.selectForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP,
                id(firstRecommendation));

        LocalDate date = LocalDate.now().plusDays(1);
        ScheduleResponse custom = schedules.create(
                fixture.ownerId(), fixture.tripId(),
                new ScheduleItemService.ScheduleCommand(
                        "직접 넣은 일정", date, LocalTime.of(18, 0),
                        ScheduleType.MAIN, null));
        assertNoActiveRoutes(fixture.tripId());

        recommendItinerary(fixture);
        schedules.update(
                fixture.ownerId(), fixture.tripId(), custom.id(),
                new ScheduleItemService.SchedulePatch(
                        "바꾼 일정", null, null, null,
                        null, false, true));
        assertNoActiveRoutes(fixture.tripId());

        recommendItinerary(fixture);
        List<Long> scheduleIds = new ArrayList<>(schedules.list(
                        fixture.ownerId(), fixture.tripId()).stream()
                .map(ScheduleResponse::id)
                .toList());
        Collections.reverse(scheduleIds);
        schedules.reorder(fixture.ownerId(), fixture.tripId(), scheduleIds);
        assertNoActiveRoutes(fixture.tripId());

        recommendItinerary(fixture);
        jdbc.sql("""
                INSERT INTO travel_supplies
                    (trip_id, plan_item_id, name, quantity, source_type, created_by)
                VALUES (?, ?, '삭제 일정 준비물', 1, 'MANUAL', ?)
                """).params(fixture.tripId(), custom.id(), fixture.ownerId()).update();
        jdbc.sql("""
                INSERT INTO notifications
                    (user_id, notification_type, title, content, trip_id, plan_item_id)
                VALUES (?, 'SCHEDULE', '일정 알림', '삭제 일정 알림', ?, ?)
                """).params(fixture.ownerId(), fixture.tripId(), custom.id()).update();
        schedules.delete(fixture.ownerId(), fixture.tripId(), custom.id());
        assertNoActiveRoutes(fixture.tripId());
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM travel_supplies
                WHERE trip_id = ? AND plan_item_id IS NULL
                """).param(fixture.tripId()).query(Long.class).single()).isEqualTo(1L);
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM notifications
                WHERE trip_id = ? AND plan_item_id IS NULL
                """).param(fixture.tripId()).query(Long.class).single()).isEqualTo(1L);
    }

    private Fixture fixture(String prefix) {
        long ownerId = users.create(prefix + System.nanoTime() % 1_000_000).id();
        LocalDate date = LocalDate.now().plusDays(1);
        long tripId = trips.create(new TripService.CreateTrip(
                ownerId,
                prefix + " 여행",
                date,
                date,
                DepartureMode.TOGETHER,
                date.atTime(9, 0),
                1L,
                1L,
                null,
                null,
                2L,
                1L
        )).id();
        surveys.respond(tripId, ownerId, List.of(
                answer("q01"), answer("q02"), answer("q03"),
                answer("q04"), answer("q05"), answer("q06"),
                answer("q07"), answer("q08"), answer("q09")
        ));
        plans.generate(tripId);
        return new Fixture(ownerId, tripId);
    }

    private void recommendItinerary(Fixture fixture) {
        routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null);
        Assertions.assertThat(activeRoutes(fixture.tripId(), RoutePhase.IN_TRIP))
                .isEqualTo(2L);
    }

    private long activeRoutes(long tripId, RoutePhase phase) {
        return jdbc.sql("""
                SELECT COUNT(*)
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                WHERE p.trip_id = ? AND r.phase = ?
                  AND r.status IN ('RECOMMENDED', 'SELECTED')
                """)
                .params(tripId, phase.name())
                .query(Long.class)
                .single();
    }

    private void assertNoActiveRoutes(long tripId) {
        Long count = jdbc.sql("""
                SELECT COUNT(*)
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                WHERE p.trip_id = ? AND r.status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(tripId)
                .query(Long.class)
                .single();
        Assertions.assertThat(count).isZero();
    }

    private long participantId(long tripId, long userId) {
        return jdbc.sql("""
                SELECT id FROM trip_participants
                WHERE trip_id = ? AND user_id = ? AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .query(Long.class)
                .single();
    }

    private SurveyResponseItem answer(String questionId) {
        SurveyResponseItem item = new SurveyResponseItem();
        item.setQuestionId(questionId);
        item.setOptionId("a");
        return item;
    }

    private long id(RouteResponse value) {
        return value.id();
    }

    private record Fixture(long ownerId, long tripId) {
    }
}
