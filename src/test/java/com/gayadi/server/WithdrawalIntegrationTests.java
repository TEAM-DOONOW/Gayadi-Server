package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.favorite.FavoritePlaceService;
import com.gayadi.server.friendship.FriendshipService;
import com.gayadi.server.invitation.InvitationService;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.survey.SurveyController;
import com.gayadi.server.survey.SurveyService;
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

@SpringBootTest
class WithdrawalIntegrationTests {

    @Autowired UserService users;
    @Autowired TripService trips;
    @Autowired InvitationService invitations;
    @Autowired SurveyService surveys;
    @Autowired FavoritePlaceService favorites;
    @Autowired FriendshipService friendships;
    @Autowired ScheduleItemService schedules;
    @Autowired JdbcClient jdbc;

    @Test
    void withdrawalRemovesOrAnonymizesPersonalRows() {
        long ownerId = id(users.create("탈퇴여행장"));
        long userId = id(users.create("탈퇴대상"));
        long friendId = id(users.create("탈퇴친구"));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long tripId = id(trips.createForUser(
                ownerId, "탈퇴 확인 여행", tomorrow, tomorrow, List.of("서울")));

        String code = RowSupport.strValue(
                invitations.create(tripId, ownerId, userId, null), "code");
        invitations.join(userId, code, null, null);
        schedules.create(userId, tripId, new ScheduleItemService.ScheduleCommand(
                "탈퇴자가 만든 일정", tomorrow, LocalTime.NOON,
                ScheduleItemService.ScheduleType.MAIN, null));
        surveys.respond(null, userId, List.of(
                answer("q01"), answer("q02"), answer("q03"),
                answer("q04"), answer("q05"), answer("q06"),
                answer("q07"), answer("q08"), answer("q09")));
        favorites.save(userId, 1L, null);
        friendships.create(userId, friendId);
        jdbc.sql("""
                INSERT INTO user_devices (user_id, device_id, platform)
                VALUES (?, 'withdraw-device', 'ANDROID')
                """).param(userId).update();
        jdbc.sql("""
                INSERT INTO social_login_accounts (user_id, provider, provider_subject)
                VALUES (?, 'KAKAO', 'withdraw-subject')
                """).param(userId).update();
        jdbc.sql("""
                INSERT INTO places
                    (owner_user_id, source, visibility, name, category,
                     latitude, longitude, region_id, status)
                VALUES (?, 'USER', 'PRIVATE', '개인 장소', 'ETC', 37, 127, 1, 'ACTIVE')
                """).param(userId).update();
        long participantId = jdbc.sql("""
                SELECT id FROM trip_participants WHERE trip_id = ? AND user_id = ?
                """).params(tripId, userId).query(Long.class).single();
        jdbc.sql("""
                INSERT INTO travel_supplies
                    (trip_id, assigned_member_id, name, quantity, source_type, created_by)
                VALUES (?, ?, '공유 준비물', 1, 'MANUAL', ?)
                """).params(tripId, participantId, ownerId).update();
        long supplyId = jdbc.sql("""
                SELECT id FROM travel_supplies WHERE trip_id = ? AND name = '공유 준비물'
                """).param(tripId).query(Long.class).single();

        users.withdraw(userId);

        Assertions.assertThat(count("survey_attempts", "user_id", userId)).isZero();
        Assertions.assertThat(count("user_favorite_places", "user_id", userId)).isZero();
        Assertions.assertThat(count("user_devices", "user_id", userId)).isZero();
        Assertions.assertThat(count("social_login_accounts", "user_id", userId)).isZero();
        Assertions.assertThat(count("trip_participants", "user_id", userId)).isZero();
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM friendships
                WHERE first_user_id = ? OR second_user_id = ?
                """).params(userId, userId).query(Long.class).single()).isZero();
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM travel_invitations
                WHERE inviter_id = ? OR invitee_user_id = ?
                """).params(userId, userId).query(Long.class).single()).isZero();
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM places
                WHERE owner_user_id = ?
                """).param(userId).query(Long.class).single()).isZero();
        Assertions.assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM places
                WHERE name = '삭제된 장소' AND status = 'DELETED'
                """).query(Long.class).single()).isEqualTo(1L);
        Assertions.assertThat(jdbc.sql("SELECT created_by FROM travel_plans WHERE trip_id = ?")
                .param(tripId).query(Long.class).single()).isEqualTo(ownerId);
        Assertions.assertThat(jdbc.sql("SELECT status FROM users WHERE id = ?")
                .param(userId).query(String.class).single()).isEqualTo("WITHDRAW");
        Assertions.assertThat(jdbc.sql("SELECT COUNT(*) FROM travel_supplies WHERE id = ?")
                .param(supplyId).query(Long.class).single()).isEqualTo(1L);
        Assertions.assertThat(jdbc.sql("SELECT assigned_member_id FROM travel_supplies WHERE id = ?")
                .param(supplyId).query(Long.class).optional()).isEmpty();
        Assertions.assertThat(users.isActive(userId)).isFalse();
    }

    private long count(String table, String column, long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")
                .param(userId).query(Long.class).single();
    }

    private SurveyController.ResponseItem answer(String questionId) {
        SurveyController.ResponseItem item = new SurveyController.ResponseItem();
        item.setQuestionId(questionId);
        item.setOptionId("a");
        return item;
    }

    private long id(Map<String, Object> value) {
        return RowSupport.longValue(value, "id");
    }
}
