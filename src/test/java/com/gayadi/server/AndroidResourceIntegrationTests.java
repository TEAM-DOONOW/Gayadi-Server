package com.gayadi.server;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.favorite.FavoriteErrorCode;
import com.gayadi.server.favorite.FavoritePlaceService;
import com.gayadi.server.invitation.InvitationService;
import com.gayadi.server.invitation.InvitationErrorCode;
import com.gayadi.server.legal.LegalDocumentService;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.TripErrorCode;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@SpringBootTest
class AndroidResourceIntegrationTests {

    @Autowired UserService users;
    @Autowired TripService trips;
    @Autowired InvitationService invitations;
    @Autowired FavoritePlaceService favorites;
    @Autowired LegalDocumentService legalDocuments;
    @Autowired JdbcClient jdbc;

    @Test
    void invitationFavoriteAndPublicContentUseDatabase() {
        long ownerId = id(users.create("초대한 사람"));
        long memberId = id(users.create("초대받은 사람"));
        long codeMemberId = id(users.create("공유 코드 참여자"));
        long secondCodeMemberId = id(users.create("공유 코드 참여자 둘"));
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        long tripId = id(trips.createForUser(
                ownerId, "제주 여행", tomorrow, tomorrow.plusDays(2), List.of("제주")));
        String tripInviteCode = RowSupport.strValue(trips.view(tripId), "inviteCode");
        Assertions.assertThat(tripInviteCode).matches("G[A-Z0-9]{5}");

        invitations.join(codeMemberId, tripInviteCode, null, null);
        invitations.join(secondCodeMemberId, tripInviteCode, null, null);
        Assertions.assertThat(trips.members(tripId)).hasSize(3);

        Map<String, Object> invitation = invitations.create(tripId, ownerId, memberId, null);
        String inviteCode = RowSupport.strValue(invitation, "code");
        Assertions.assertThat(inviteCode).matches("[A-Z0-9]{8}");
        Assertions.assertThatThrownBy(() -> invitations.updateStatus(
                        tripId, id(invitation), ownerId, InvitationService.InvitationDecision.DECLINED))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(InvitationErrorCode.INVITATION_DECLINE_FORBIDDEN));

        Map<String, Object> membership = invitations.join(memberId, inviteCode, null, null);
        Assertions.assertThat(RowSupport.longValue(
                (Map<String, Object>) membership.get("trip"), "id")).isEqualTo(tripId);
        Assertions.assertThat(RowSupport.strValue(
                (Map<String, Object>) membership.get("participant"), "status")).isEqualTo("JOINED");
        Assertions.assertThat(RowSupport.longValue(
                (Map<String, Object>) membership.get("participant"), "id")).isEqualTo(memberId);
        Assertions.assertThat(trips.members(tripId)).hasSize(4);
        Assertions.assertThatThrownBy(() -> invitations.join(
                        codeMemberId, tripInviteCode, null, null))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(TripErrorCode.TRIP_ALREADY_JOINED));

        favorites.save(memberId, 1L, "다시 가고 싶은 곳");
        Assertions.assertThat(favorites.list(memberId))
                .singleElement()
                .satisfies(place -> Assertions.assertThat(RowSupport.longValue(place, "id")).isEqualTo(1L));
        favorites.delete(memberId, 1L);
        Assertions.assertThat(favorites.list(memberId)).isEmpty();
        Assertions.assertThatThrownBy(() -> favorites.delete(memberId, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> Assertions.assertThat(exception.getErrorCode())
                                .isEqualTo(FavoriteErrorCode.FAVORITE_PLACE_NOT_FOUND));

        Map<String, Object> terms = legalDocuments.get("terms-of-service");
        Assertions.assertThat(terms.get("title")).isEqualTo("가야디 이용약관");
        Assertions.assertThat((List<?>) terms.get("sections")).isNotEmpty();

        Integer questionCount = jdbc.sql("SELECT COUNT(*) FROM survey_questions WHERE survey_id = 2")
                .query(Integer.class).single();
        Integer resultCount = jdbc.sql("SELECT COUNT(*) FROM travel_personality_results")
                .query(Integer.class).single();
        Assertions.assertThat(questionCount).isEqualTo(9);
        Assertions.assertThat(resultCount).isEqualTo(8);
        Assertions.assertThat(jdbc.sql("SELECT max_members FROM trips WHERE id = ?")
                .param(tripId).query(Integer.class).single()).isEqualTo(20);
    }

    private long id(Map<String, Object> row) {
        return RowSupport.longValue(row, "id");
    }
}
