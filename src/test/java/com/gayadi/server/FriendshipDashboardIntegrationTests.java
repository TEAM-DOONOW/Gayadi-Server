package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.dashboard.DashboardService;
import com.gayadi.server.friendship.FriendshipErrorCode;
import com.gayadi.server.friendship.FriendshipService;
import com.gayadi.server.friendship.FriendshipStatus;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.travel.TripService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@SpringBootTest
class FriendshipDashboardIntegrationTests {

    @Autowired UserService users;
    @Autowired FriendshipService friendships;
    @Autowired DashboardService dashboards;
    @Autowired TripService trips;
    @Autowired ScheduleItemService schedules;
    @Autowired JdbcClient jdbc;

    @Test
    void friendshipPermissionsBlockingAndPrivateFieldsAreEnforced() {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        long requesterId = id(users.create("요청자" + suffix));
        String recipientNickname = "수신자" + suffix;
        long recipientId = id(users.create(recipientNickname));

        Map<String, Object> request = friendships.create(requesterId, recipientId);
        long friendshipId = id(request);
        Assertions.assertThat(request.get("status")).isEqualTo("PENDING");

        Assertions.assertThatThrownBy(() -> friendships.update(
                requesterId, friendshipId, FriendshipStatus.ACCEPTED, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FriendshipErrorCode.FRIENDSHIP_DECISION_FORBIDDEN));

        Map<String, Object> accepted = friendships.update(
                recipientId, friendshipId, FriendshipStatus.ACCEPTED, 0);
        Assertions.assertThat(accepted.get("status")).isEqualTo("ACCEPTED");
        Assertions.assertThatThrownBy(() -> friendships.create(recipientId, requesterId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FriendshipErrorCode.FRIENDSHIP_ALREADY_ACCEPTED));

        List<Map<String, Object>> search = friendships.searchUsers(requesterId, recipientNickname, 10);
        Assertions.assertThat(search).singleElement()
                .satisfies(result -> {
                    Assertions.assertThat(result.get("id")).isEqualTo(recipientId);
                    Assertions.assertThat(result).doesNotContainKeys("email", "password", "passwordHash");
                });
        Assertions.assertThat(search.toString()).doesNotContainIgnoringCase("email", "password");

        Map<String, Object> blocked = friendships.update(
                requesterId, friendshipId, FriendshipStatus.BLOCKED,
                RowSupport.intValue(accepted, "version"));
        Assertions.assertThat(blocked.get("blockedByMe")).isEqualTo(true);
        Assertions.assertThat(friendships.list(requesterId, "BLOCKED", 10, 0)).hasSize(1);
        Assertions.assertThat(friendships.list(recipientId, null, 10, 0)).isEmpty();
        Assertions.assertThat(friendships.searchUsers(recipientId, "요청자", 10)).isEmpty();
        Assertions.assertThatThrownBy(() -> friendships.delete(recipientId, friendshipId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND));

        friendships.delete(requesterId, friendshipId);
        Map<String, Object> reverseRequest = friendships.create(recipientId, requesterId);
        Assertions.assertThat(reverseRequest.get("status")).isEqualTo("PENDING");
    }

    @Test
    void oppositeRequestsCreateOnlyOneDatabaseRow() throws Exception {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        long firstUserId = id(users.create("동시요청가" + suffix));
        long secondUserId = id(users.create("동시요청나" + suffix));
        CountDownLatch start = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> createAfterStart(start, firstUserId, secondUserId));
            Future<Boolean> second = executor.submit(() -> createAfterStart(start, secondUserId, firstUserId));
            start.countDown();
            Assertions.assertThat(List.of(first.get(), second.get()))
                    .containsExactlyInAnyOrder(true, false);
        }

        long lower = Math.min(firstUserId, secondUserId);
        long higher = Math.max(firstUserId, secondUserId);
        Long count = jdbc.sql("""
                SELECT COUNT(*) FROM friendships
                WHERE first_user_id = ? AND second_user_id = ?
                """)
                .params(lower, higher)
                .query(Long.class)
                .single();
        Assertions.assertThat(count).isEqualTo(1L);
    }

    @Test
    void dashboardUsesStoredSchedulesWithoutInventedConditions() {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        long ownerId = id(users.create("여행홈" + suffix));
        LocalDate today = LocalDate.now();
        long tripId = id(trips.createForUser(
                ownerId, "여행 홈 확인", today, today.plusDays(1), List.of("서울")));

        ScheduleItemService.ScheduleCommand morning = new ScheduleItemService.ScheduleCommand(
                "오전 일정", today, LocalTime.of(10, 0), ScheduleItemService.ScheduleType.MAIN, null);
        Map<String, Object> first = schedules.create(ownerId, tripId, morning);
        schedules.create(ownerId, tripId, new ScheduleItemService.ScheduleCommand(
                "오후 일정", today, LocalTime.of(15, 0), ScheduleItemService.ScheduleType.MAIN, null));
        schedules.update(ownerId, tripId, id(first), morning, true);

        Map<String, Object> dashboard = dashboards.dashboard(ownerId, tripId);
        Assertions.assertThat(dashboard.get("participantCount")).isEqualTo(1);
        Assertions.assertThat((List<?>) dashboard.get("schedules")).hasSize(2);
        Assertions.assertThat((List<?>) dashboard.get("tripDays")).hasSize(2);
        Assertions.assertThat(((Map<?, ?>) ((List<?>) dashboard.get("tripDays")).getFirst()).get("date"))
                .isEqualTo(today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        Assertions.assertThat((List<?>) dashboard.get("pendingChangeProposals")).isEmpty();
        Map<?, ?> progress = (Map<?, ?>) dashboard.get("progress");
        Assertions.assertThat(progress.get("scheduleCount")).isEqualTo(2);
        Assertions.assertThat(progress.get("visitedCount")).isEqualTo(1L);
        Assertions.assertThat(progress.get("percentage")).isEqualTo(50);
        Assertions.assertThat(dashboard).doesNotContainKeys("weather", "crowd", "congestion");
    }

    @Test
    void schedulePatchCanChangeOnlyVisitedState() {
        long ownerId = id(users.create("부분수정" + System.nanoTime() % 1_000_000));
        LocalDate today = LocalDate.now();
        long tripId = id(trips.createForUser(
                ownerId, "일정 부분 수정", today, today, List.of("서울")));
        Map<String, Object> created = schedules.create(ownerId, tripId,
                new ScheduleItemService.ScheduleCommand(
                        "그대로 둘 일정", today, LocalTime.of(11, 30),
                        ScheduleItemService.ScheduleType.MAIN, null));

        Map<String, Object> updated = schedules.update(ownerId, tripId, id(created),
                new ScheduleItemService.SchedulePatch(
                        null, null, null, null, null, false, true));

        Assertions.assertThat(updated.get("title")).isEqualTo("그대로 둘 일정");
        Assertions.assertThat(updated.get("time")).isEqualTo("11:30");
        Assertions.assertThat(updated.get("isVisited")).isEqualTo(true);
    }

    @Test
    void changingTripStartDateRecalculatesExistingScheduleDay() {
        long ownerId = id(users.create("일차재계산" + System.nanoTime() % 1_000_000));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long tripId = id(trips.createForUser(
                ownerId, "일차 재계산", tomorrow, tomorrow.plusDays(2), List.of("서울")));
        schedules.create(ownerId, tripId, new ScheduleItemService.ScheduleCommand(
                "기존 일정", tomorrow.plusDays(1), LocalTime.NOON,
                ScheduleItemService.ScheduleType.MAIN, null));
        int version = RowSupport.intValue(trips.view(tripId), "version");

        trips.update(ownerId, tripId, "일차 재계산", tomorrow.minusDays(1),
                tomorrow.plusDays(2), List.of("서울"), version);

        Integer dayNumber = jdbc.sql("""
                SELECT day_number FROM travel_plans
                WHERE trip_id = ? AND plan_date = ?
                """)
                .params(tripId, tomorrow.plusDays(1))
                .query(Integer.class)
                .single();
        Assertions.assertThat(dayNumber).isEqualTo(3);
    }

    private boolean createAfterStart(CountDownLatch start, long requesterId, long targetUserId)
            throws InterruptedException {
        start.await();
        try {
            friendships.create(requesterId, targetUserId);
            return true;
        } catch (BusinessException exception) {
            Assertions.assertThat(exception.getErrorCode().status().value()).isEqualTo(409);
            return false;
        }
    }

    private long id(Map<String, Object> row) {
        return RowSupport.longValue(row, "id");
    }
}
