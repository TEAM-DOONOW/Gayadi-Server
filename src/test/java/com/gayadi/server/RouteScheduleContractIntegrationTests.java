package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.route.RoutePhase;
import com.gayadi.server.route.TransportMode;
import com.gayadi.server.route.RouteProvider;
import com.gayadi.server.route.KakaoDirectionsRouteProvider;
import com.gayadi.server.route.RouteErrorCode;
import com.gayadi.server.common.Location;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
    @MockitoBean KakaoDirectionsRouteProvider carProvider;

    @Test
    void carModeIsOptimizedStoredAndReturnedAfterSelection() {
        Fixture fixture = fixture("자동차최적화");
        long planId = replaceWithFourUntimedStops(fixture);
        stubCarProvider();
        RouteResponse recommendation = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null, TransportMode.CAR);
        Assertions.assertThat(recommendation.transportMode()).isEqualTo("CAR");
        Assertions.assertThat(recommendation.provider()).isEqualTo("KAKAO_DIRECTIONS");
        Assertions.assertThat(recommendation.fallback()).isFalse();
        Assertions.assertThat(recommendation.stops()).extracting(Location::label)
                .containsExactly("route-A", "route-C", "route-B", "route-D");
        Assertions.assertThat(recommendation.durationMinutes()).isEqualTo(3);
        Assertions.assertThat(recommendation.transferCount()).isZero();
        Assertions.assertThat(recommendation.options()).hasSize(1);
        RouteResponse selected = routes.selectForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, recommendation.id());
        Assertions.assertThat(selected.transportMode()).isEqualTo("CAR");
        Assertions.assertThat(selected.stops()).isEqualTo(recommendation.stops());
        Assertions.assertThat(selected.segments()).isEqualTo(recommendation.segments());
        Assertions.assertThat(routes.selectionsForUser(fixture.tripId(), fixture.ownerId()))
                .extracting(RouteResponse::transportMode).containsExactly("CAR");

        // 시간이 고정된 일정은 재배치하지 않습니다.
        jdbc.sql("UPDATE travel_plan_items SET planned_start = ? WHERE plan_id = ? AND sequence_no = 2")
                .params(LocalDate.now().plusDays(1).atTime(12, 0), planId).update();
        RouteResponse fixed = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null, TransportMode.CAR);
        Assertions.assertThat(fixed.stops()).extracting(Location::label)
                .containsExactly("route-A", "route-B", "route-C", "route-D");
    }

    @Test
    void failedCarCalculationKeepsPreviousRecommendations() {
        Fixture fixture = fixture("자동차오류");
        recommendItinerary(fixture);
        when(carProvider.transportMode()).thenReturn(TransportMode.CAR);
        when(carProvider.estimateSegments(anyList(), anyString()))
                .thenThrow(new BusinessException(RouteErrorCode.KAKAO_ROUTE_UNAVAILABLE));
        Assertions.assertThatThrownBy(() -> routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null, TransportMode.CAR))
                .isInstanceOf(BusinessException.class);
        Assertions.assertThat(activeRoutes(fixture.tripId(), RoutePhase.IN_TRIP)).isEqualTo(4L);
    }

    @Test
    void doesNotMoveStopsAcrossDaysOrCoordinateLessScheduleItems() {
        Fixture fixture = fixture("날짜경계");
        long firstPlanId = replaceWithFourUntimedStops(fixture);
        jdbc.sql("""
                INSERT INTO travel_plans (trip_id, plan_date, day_number, title, source_type, created_by)
                VALUES (?, ?, 2, '2일차', 'MANUAL', ?)
                """).params(fixture.tripId(), LocalDate.now().plusDays(2), fixture.ownerId()).update();
        long secondPlanId = jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ? AND day_number = 2")
                .param(fixture.tripId()).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO travel_plan_items (plan_id, place_id, item_type, title, sequence_no)
                SELECT ?, place_id, item_type, title, sequence_no
                FROM travel_plan_items WHERE plan_id = ?
                """).params(secondPlanId, firstPlanId).update();
        stubCarProvider();
        RouteResponse recommendation = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null, TransportMode.CAR);
        Assertions.assertThat(recommendation.stops()).extracting(Location::label)
                .containsExactly("route-A", "route-C", "route-B", "route-D",
                        "route-A", "route-C", "route-B", "route-D");

        // 좌표 없는 일정이 두 장소 사이에 있으면 그 경계를 넘어 이동하지 않습니다.
        jdbc.sql("UPDATE travel_plan_items SET sequence_no = sequence_no + 100 WHERE plan_id = ?")
                .param(firstPlanId).update();
        jdbc.sql("UPDATE travel_plan_items SET sequence_no = (sequence_no - 100) * 2 WHERE plan_id = ?")
                .param(firstPlanId).update();
        jdbc.sql("""
                INSERT INTO travel_plan_items (plan_id, item_type, title, sequence_no)
                VALUES (?, 'CUSTOM', '고정 활동', 5)
                """).param(firstPlanId).update();
        RouteResponse withBarrier = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null, TransportMode.CAR);
        Assertions.assertThat(withBarrier.stops()).extracting(Location::label)
                .containsExactly("route-A", "route-B", "route-C", "route-D",
                        "route-A", "route-C", "route-B", "route-D");
    }

    private void stubCarProvider() {
        when(carProvider.transportMode()).thenReturn(TransportMode.CAR);
        when(carProvider.providerName()).thenReturn("KAKAO_DIRECTIONS");
        when(carProvider.estimateSegments(anyList(), anyString())).thenAnswer(invocation -> {
            List<Location> stops = invocation.getArgument(0);
            String edge = stops.getFirst().label() + ":" + stops.getLast().label();
            int minutes = List.of("route-A:route-C", "route-C:route-B", "route-B:route-D")
                    .contains(edge) ? 1 : 20;
            return List.of(new RouteProvider.RouteEstimate(minutes, 0, 100, "자동차", "KAKAO_DIRECTIONS"));
        });
    }

    private long replaceWithFourUntimedStops(Fixture fixture) {
        long planId = jdbc.sql("SELECT id FROM travel_plans WHERE trip_id = ?")
                .param(fixture.tripId()).query(Long.class).single();
        jdbc.sql("DELETE FROM travel_plan_items WHERE plan_id = ?").param(planId).update();
        for (int i = 0; i < 4; i++) {
            String name = "route-" + (char) ('A' + i);
            jdbc.sql("""
                    INSERT INTO places (trip_id, source, name, category, latitude, longitude, region_id)
                    VALUES (?, 'TRIP', ?, 'ATTRACTION', ?, ?, 1)
                    """).params(fixture.tripId(), name, 37.0 + i * 0.01, 127.0 + i * 0.01).update();
            long placeId = jdbc.sql("SELECT id FROM places WHERE trip_id = ? AND name = ?")
                    .params(fixture.tripId(), name).query(Long.class).single();
            jdbc.sql("""
                    INSERT INTO travel_plan_items (plan_id, place_id, item_type, title, sequence_no)
                    VALUES (?, ?, 'PLACE', ?, ?)
                    """).params(planId, placeId, name, i + 1).update();
        }
        return planId;
    }


    @Test
    void itineraryContainsEveryPlaceSegmentAndFourStoredOptions() {
        Assertions.assertThat(Arrays.stream(
                        RouteRecommendationRequest.class.getRecordComponents())
                .map(component -> component.getName())
                .toList())
                .containsExactly("type", "userId", "transportMode");
        Fixture fixture = fixture("전체동선");

        RouteResponse recommendation = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.IN_TRIP, null);

        Assertions.assertThat(recommendation.options())
                .extracting(RouteResponse::optionId)
                .containsExactly("balanced", "crowd", "walk", "bicycle");
        Assertions.assertThat(recommendation.optionId()).isEqualTo("balanced");

        List<?> stops = recommendation.stops();
        var segments = recommendation.segments();
        Assertions.assertThat(stops).hasSize(3);
        Assertions.assertThat(segments).hasSize(stops.size() - 1);
        Assertions.assertThat(segments)
                .extracting(segment -> segment.order())
                .containsExactly(1, 2);
        Assertions.assertThat(activeRoutes(fixture.tripId(), RoutePhase.IN_TRIP))
                .isEqualTo(4L);

        RouteResponse home = routes.recommendForUser(
                fixture.tripId(), fixture.ownerId(), RoutePhase.RETURN, fixture.ownerId());
        Assertions.assertThat(home.options())
                .extracting(RouteResponse::optionId)
                .containsExactly("home-fast", "home-rest", "walk", "bicycle");
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
    void walkingAndCyclingCanBeSelectedAndReloadedInEveryPhase() {
        Fixture fixture = fixture("도보자전거");
        for (RoutePhase phase : RoutePhase.values()) {
            RouteResponse recommendation = routes.recommendForUser(
                    fixture.tripId(), fixture.ownerId(), phase, null);
            RouteResponse walk = recommendation.options().get(2);
            RouteResponse bicycle = recommendation.options().get(3);
            Assertions.assertThat(walk.durationMinutes()).isGreaterThanOrEqualTo(bicycle.durationMinutes());
            if (phase == RoutePhase.IN_TRIP) {
                Assertions.assertThat(walk.durationMinutes()).isGreaterThan(bicycle.durationMinutes());
            }
            for (RouteResponse option : List.of(walk, bicycle)) {
                Assertions.assertThat(option.transportMode())
                        .isEqualTo(option.optionId().equals("walk") ? "WALK" : "BICYCLE");
                Assertions.assertThat(option.provider()).isEqualTo("LOCAL_ESTIMATE");
                Assertions.assertThat(option.fallback()).isFalse();
                Assertions.assertThat(option.fare()).isZero();
                Assertions.assertThat(option.transferCount()).isZero();
                Assertions.assertThat(option.segments()).hasSize(recommendation.stops().size() - 1);
                Assertions.assertThat(option.durationMinutes()).isEqualTo(option.segments().stream()
                        .mapToInt(segment -> segment.durationMinutes()).sum());
                Assertions.assertThat(option.segments()).allSatisfy(segment -> {
                    Assertions.assertThat(segment.fare()).isZero();
                    Assertions.assertThat(segment.transferCount()).isZero();
                    Assertions.assertThat(segment.summary()).contains("직선거리");
                });
                RouteResponse selected = routes.selectForUser(
                        fixture.tripId(), fixture.ownerId(), phase,
                        null, option.optionId(), null);
                Assertions.assertThat(selected.id()).isEqualTo(option.id());
                Assertions.assertThat(selected.transportMode()).isEqualTo(option.transportMode());
                Assertions.assertThat(selected.durationMinutes()).isEqualTo(option.durationMinutes());
                Assertions.assertThat(selected.status()).isEqualTo("SELECTED");
            }
        }
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
                .isEqualTo(4L);
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
