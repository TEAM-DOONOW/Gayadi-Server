package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.dashboard.DashboardService;
import com.gayadi.server.dashboard.dto.response.DashboardResponse;
import com.gayadi.server.friendship.FriendshipErrorCode;
import com.gayadi.server.friendship.FriendshipService;
import com.gayadi.server.friendship.model.FriendshipStatus;
import com.gayadi.server.friendship.dto.response.FriendshipResponse;
import com.gayadi.server.friendship.dto.response.UserSearchResponse;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.schedule.model.ScheduleType;
import com.gayadi.server.schedule.dto.response.ScheduleResponse;
import com.gayadi.server.travel.TripService;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
        long requesterId = users.create("요청자" + suffix).id();
        String recipientNickname = "수신자" + suffix;
        long recipientId = users.create(recipientNickname).id();

        FriendshipResponse request = friendships.create(requesterId, recipientId);
        long friendshipId = request.id();
        Assertions.assertThat(request.status()).isEqualTo(FriendshipStatus.PENDING);

        Assertions.assertThatThrownBy(() -> friendships.update(
                requesterId, friendshipId, FriendshipStatus.ACCEPTED, 0))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FriendshipErrorCode.FRIENDSHIP_DECISION_FORBIDDEN));

        FriendshipResponse accepted = friendships.update(
                recipientId, friendshipId, FriendshipStatus.ACCEPTED, 0);
        Assertions.assertThat(accepted.status()).isEqualTo(FriendshipStatus.ACCEPTED);
        Assertions.assertThatThrownBy(() -> friendships.create(recipientId, requesterId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FriendshipErrorCode.FRIENDSHIP_ALREADY_ACCEPTED));

        List<UserSearchResponse> search = friendships.searchUsers(requesterId, recipientNickname, 10);
        Assertions.assertThat(search).singleElement()
                .satisfies(result -> {
                    Assertions.assertThat(result.id()).isEqualTo(recipientId);
                });
        Assertions.assertThat(search.toString()).doesNotContainIgnoringCase("email", "password");

        FriendshipResponse blocked = friendships.update(
                requesterId, friendshipId, FriendshipStatus.BLOCKED,
                accepted.version());
        Assertions.assertThat(blocked.blockedByMe()).isTrue();
        Assertions.assertThat(friendships.list(requesterId, "BLOCKED", 10, 0)).hasSize(1);
        Assertions.assertThat(friendships.list(recipientId, null, 10, 0)).isEmpty();
        Assertions.assertThat(friendships.searchUsers(recipientId, "요청자", 10)).isEmpty();
        Assertions.assertThatThrownBy(() -> friendships.delete(recipientId, friendshipId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FriendshipErrorCode.FRIENDSHIP_NOT_FOUND));

        friendships.delete(requesterId, friendshipId);
        FriendshipResponse reverseRequest = friendships.create(recipientId, requesterId);
        Assertions.assertThat(reverseRequest.status()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void oppositeRequestsCreateOnlyOneDatabaseRow() throws Exception {
        String suffix = Long.toString(System.nanoTime() % 1_000_000);
        long firstUserId = users.create("동시요청가" + suffix).id();
        long secondUserId = users.create("동시요청나" + suffix).id();
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
        long ownerId = users.create("여행홈" + suffix).id();
        LocalDate today = LocalDate.now();
        long tripId = trips.createForUser(
                ownerId, "여행 홈 확인", today, today.plusDays(1), List.of("서울")).id();

        ScheduleItemService.ScheduleCommand morning = new ScheduleItemService.ScheduleCommand(
                "오전 일정", today, LocalTime.of(10, 0), ScheduleType.MAIN, null);
        ScheduleResponse first = schedules.create(ownerId, tripId, morning);
        schedules.create(ownerId, tripId, new ScheduleItemService.ScheduleCommand(
                "오후 일정", today, LocalTime.of(15, 0), ScheduleType.MAIN, null));
        schedules.update(ownerId, tripId, first.id(), morning, true);

        DashboardResponse dashboard = dashboards.dashboard(ownerId, tripId);
        Assertions.assertThat(dashboard.participantCount()).isEqualTo(1);
        Assertions.assertThat(dashboard.schedules()).hasSize(2);
        Assertions.assertThat(dashboard.tripDays()).hasSize(2);
        Assertions.assertThat(dashboard.tripDays().getFirst().date())
                .isEqualTo(today.format(java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        Assertions.assertThat(dashboard.pendingChangeProposals()).isEmpty();
        Assertions.assertThat(dashboard.progress().scheduleCount()).isEqualTo(2);
        Assertions.assertThat(dashboard.progress().visitedCount()).isEqualTo(1L);
        Assertions.assertThat(dashboard.progress().percentage()).isEqualTo(50);
    }

    @Test
    void schedulePatchCanChangeOnlyVisitedState() {
        long ownerId = users.create("부분수정" + System.nanoTime() % 1_000_000).id();
        LocalDate today = LocalDate.now();
        long tripId = trips.createForUser(
                ownerId, "일정 부분 수정", today, today, List.of("서울")).id();
        ScheduleResponse created = schedules.create(ownerId, tripId,
                new ScheduleItemService.ScheduleCommand(
                        "그대로 둘 일정", today, LocalTime.of(11, 30),
                        ScheduleType.MAIN, null));

        ScheduleResponse updated = schedules.update(ownerId, tripId, created.id(),
                new ScheduleItemService.SchedulePatch(
                        null, null, null, null, null, false, true));

        Assertions.assertThat(updated.title()).isEqualTo("그대로 둘 일정");
        Assertions.assertThat(updated.time()).isEqualTo("11:30");
        Assertions.assertThat(updated.isVisited()).isTrue();
    }

    @Test
    void changingTripStartDateRecalculatesExistingScheduleDay() {
        long ownerId = users.create("일차재계산" + System.nanoTime() % 1_000_000).id();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long tripId = trips.createForUser(
                ownerId, "일차 재계산", tomorrow, tomorrow.plusDays(2), List.of("서울")).id();
        schedules.create(ownerId, tripId, new ScheduleItemService.ScheduleCommand(
                "기존 일정", tomorrow.plusDays(1), LocalTime.NOON,
                ScheduleType.MAIN, null));
        int version = trips.view(tripId).version();

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

}
