package com.gayadi.server.invitation;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InvitationService {

    private static final char[] CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int CODE_GENERATION_ATTEMPTS = 20;
    private static final int MAX_JOIN_ATTEMPTS_PER_WINDOW = 20;
    private static final Duration JOIN_ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private final JdbcClient jdbc;
    private final TripService trips;
    private final UserService users;
    private final KeyHelper keyHelper;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<Long, JoinAttemptWindow> joinAttempts = new ConcurrentHashMap<>();

    public InvitationService(JdbcClient jdbc, TripService trips, UserService users, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.users = users;
        this.keyHelper = keyHelper;
    }

    @Transactional
    public List<Map<String, Object>> list(long tripId, long userId, int requestedLimit, int requestedOffset) {
        trips.requireMember(tripId, userId);
        expirePendingInvitations(tripId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        int offset = Math.max(0, requestedOffset);
        return jdbc.sql("""
                SELECT i.id, i.trip_id, i.inviter_id, inviter.nickname AS inviter_nickname,
                       i.invitee_user_id, invitee.nickname AS invitee_nickname,
                       i.invite_code, i.status, i.expires_at, i.accepted_at,
                       i.rejected_at, i.canceled_at, i.created_at, i.updated_at
                FROM travel_invitations i
                JOIN users inviter ON inviter.id = i.inviter_id
                LEFT JOIN users invitee ON invitee.id = i.invitee_user_id
                WHERE i.trip_id = ?
                ORDER BY i.created_at DESC, i.id DESC
                LIMIT ? OFFSET ?
                """)
                .params(tripId, limit, offset)
                .query().listOfRows().stream()
                .map(this::toInvitationView)
                .toList();
    }

    @Transactional
    public Map<String, Object> create(
            long tripId,
            long userId,
            Long inviteeUserId,
            LocalDateTime expiresAt) {
        lockUsersInOrder(userId, inviteeUserId);
        Map<String, Object> trip = lockTrip(tripId);
        trips.requireOwner(tripId, userId);
        if (!"PLANNING".equals(RowSupport.strValue(trip, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "준비 중인 여행에서만 초대할 수 있습니다.");
        }

        if (inviteeUserId != null) {
            if (isJoinedMember(tripId, inviteeUserId)) {
                throw new ApiException(HttpStatus.CONFLICT, "이미 여행에 참여 중인 사용자입니다.");
            }
        }

        LocalDateTime expiration = expiresAt == null ? LocalDateTime.now().plusDays(7) : expiresAt;
        if (!expiration.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "초대 만료 시각은 현재보다 뒤여야 합니다.");
        }

        long invitationId;
        try {
            invitationId = keyHelper.insert("""
                    INSERT INTO travel_invitations
                        (trip_id, inviter_id, invitee_user_id, invite_code, status, expires_at)
                    VALUES (?, ?, ?, ?, 'PENDING', ?)
                    """, tripId, userId, inviteeUserId, availableCode(), expiration);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "초대 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
        return toInvitationView(invitation(invitationId, tripId));
    }

    @Transactional
    public Map<String, Object> updateStatus(
            long tripId,
            long invitationId,
            long userId,
            InvitationDecision decision) {
        Map<String, Object> current = invitation(invitationId, tripId);
        if (decision == InvitationDecision.CANCELLED) {
            trips.requireOwner(tripId, userId);
        } else {
            Object invitee = current.get("invitee_user_id");
            if (invitee == null || ((Number) invitee).longValue() != userId) {
                throw new ApiException(HttpStatus.FORBIDDEN, "초대를 받은 사용자만 거절할 수 있습니다.");
            }
        }

        String status = decision == InvitationDecision.CANCELLED ? "CANCELED" : "REJECTED";
        String timeColumn = decision == InvitationDecision.CANCELLED ? "canceled_at" : "rejected_at";
        int updated = jdbc.sql("""
                UPDATE travel_invitations
                SET status = ?, %s = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND trip_id = ? AND status = 'PENDING'
                  AND expires_at > CURRENT_TIMESTAMP
                """.formatted(timeColumn))
                .params(status, invitationId, tripId)
                .update();
        if (updated == 0) {
            throw new ApiException(HttpStatus.CONFLICT, "대기 중인 초대만 상태를 바꿀 수 있습니다.");
        }
        return toInvitationView(invitation(invitationId, tripId));
    }

    @Transactional
    public Map<String, Object> join(
            long userId,
            String inviteCode,
            Long departurePlaceId,
            Long returnPlaceId) {
        users.lockActive(userId);
        checkJoinAttemptLimit(userId);
        String normalizedCode = normalizeCode(inviteCode);

        Map<String, Object> current = jdbc.sql("""
                SELECT id, trip_id, invitee_user_id, status, expires_at
                FROM travel_invitations
                WHERE invite_code = ?
                """)
                .param(normalizedCode)
                .query().listOfRows().stream()
                .findFirst()
                .orElse(null);

        if (current == null) {
            return joinByReusableTripCode(
                    userId, normalizedCode, departurePlaceId, returnPlaceId);
        }

        long invitationId = RowSupport.longValue(current, "id");
        long tripId = RowSupport.longValue(current, "trip_id");
        Map<String, Object> lockedTrip = lockTrip(tripId);
        validateJoinableInvitation(current, userId);
        validateMemberCapacity(lockedTrip, tripId);

        if (isJoinedMember(tripId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 참여 중인 여행입니다.");
        }

        int accepted = jdbc.sql("""
                UPDATE travel_invitations
                SET status = 'ACCEPTED', invitee_user_id = ?, accepted_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND invite_code = ? AND status = 'PENDING'
                  AND expires_at > CURRENT_TIMESTAMP
                  AND (invitee_user_id IS NULL OR invitee_user_id = ?)
                """)
                .params(userId, invitationId, normalizedCode, userId)
                .update();
        if (accepted != 1) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용했거나 만료된 초대 코드입니다.");
        }

        trips.addMember(tripId, new TripService.AddMember(userId, departurePlaceId, returnPlaceId));

        trips.requireMember(tripId, userId);
        return membership(tripId, userId, invitationId);
    }

    private Map<String, Object> joinByReusableTripCode(
            long userId,
            String inviteCode,
            Long departurePlaceId,
            Long returnPlaceId) {
        Long tripId = jdbc.sql("""
                SELECT id FROM trips
                WHERE invite_code = ? AND deleted_at IS NULL
                """)
                .param(inviteCode)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "유효한 초대 코드를 찾을 수 없습니다."));

        Map<String, Object> lockedTrip = lockTrip(tripId);
        validateMemberCapacity(lockedTrip, tripId);
        if (isJoinedMember(tripId, userId)) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 참여 중인 여행입니다.");
        }
        trips.addMember(tripId, new TripService.AddMember(userId, departurePlaceId, returnPlaceId));
        return membership(tripId, userId, null);
    }

    private Map<String, Object> lockTrip(long tripId) {
        return jdbc.sql("""
                SELECT id, status, max_members
                FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    private void validateJoinableInvitation(Map<String, Object> invitation, long userId) {
        String status = RowSupport.strValue(invitation, "status");
        LocalDateTime expiresAt = localDateTime(RowSupport.value(invitation, "expires_at"));
        if (!"PENDING".equals(status) || !expiresAt.isAfter(LocalDateTime.now())) {
            throw new ApiException(HttpStatus.CONFLICT, "이미 사용했거나 만료된 초대 코드입니다.");
        }
        Object invitee = invitation.get("invitee_user_id");
        if (invitee != null && ((Number) invitee).longValue() != userId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "다른 사용자에게 발급된 초대 코드입니다.");
        }
    }

    private void validateMemberCapacity(Map<String, Object> trip, long tripId) {
        if (!"PLANNING".equals(RowSupport.strValue(trip, "status"))) {
            throw new ApiException(HttpStatus.CONFLICT, "준비 중인 여행에만 참여할 수 있습니다.");
        }
        Object value = trip.get("max_members");
        if (value == null) {
            return;
        }
        int joined = jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND status = 'JOINED'
                """)
                .param(tripId)
                .query(Integer.class)
                .single();
        if (joined >= ((Number) value).intValue()) {
            throw new ApiException(HttpStatus.CONFLICT, "여행 참여 인원이 모두 찼습니다.");
        }
    }

    private void lockUsersInOrder(long firstUserId, Long secondUserId) {
        if (secondUserId == null || firstUserId == secondUserId) {
            users.lockActive(firstUserId);
            return;
        }
        users.lockActive(Math.min(firstUserId, secondUserId));
        users.lockActive(Math.max(firstUserId, secondUserId));
    }

    private boolean isJoinedMember(long tripId, long userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM trip_participants
                WHERE trip_id = ? AND user_id = ? AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .query(Integer.class)
                .single() > 0;
    }

    private String availableCode() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            // 여행의 재사용 코드(G...)와 겹치지 않도록 개별 초대는 I로 시작한다.
            StringBuilder code = new StringBuilder("I");
            for (int index = 1; index < CODE_LENGTH; index++) {
                code.append(CODE_CHARACTERS[random.nextInt(CODE_CHARACTERS.length)]);
            }
            long count = jdbc.sql("""
                    SELECT
                      (SELECT COUNT(*) FROM travel_invitations WHERE invite_code = ?) +
                      (SELECT COUNT(*) FROM trips WHERE invite_code = ?)
                    """)
                    .params(code.toString(), code.toString())
                    .query(Long.class)
                    .single();
            if (count == 0) {
                return code.toString();
            }
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "초대 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "초대 만료 시각을 확인하지 못했습니다.");
    }

    private String normalizeCode(String inviteCode) {
        String normalized = inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{6}|[A-Z0-9]{8}")) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "여행 공유 코드는 6자리, 특정 사용자 초대 코드는 8자리여야 합니다.");
        }
        return normalized;
    }

    private void checkJoinAttemptLimit(long userId) {
        Instant now = Instant.now();
        JoinAttemptWindow window = joinAttempts.compute(userId, (ignored, current) -> {
            if (current == null || current.startedAt().plus(JOIN_ATTEMPT_WINDOW).isBefore(now)) {
                return new JoinAttemptWindow(now, new AtomicInteger(1));
            }
            current.count().incrementAndGet();
            return current;
        });
        if (window.count().get() > MAX_JOIN_ATTEMPTS_PER_WINDOW) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "초대 코드 확인 요청이 많습니다. 10분 뒤 다시 시도해 주세요.");
        }
        if (joinAttempts.size() > 10000) {
            joinAttempts.entrySet().removeIf(entry ->
                    entry.getValue().startedAt().plus(JOIN_ATTEMPT_WINDOW).isBefore(now));
        }
    }

    private void expirePendingInvitations(long tripId) {
        jdbc.sql("""
                UPDATE travel_invitations
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE trip_id = ? AND status = 'PENDING' AND expires_at <= CURRENT_TIMESTAMP
                """)
                .param(tripId)
                .update();
    }

    private Map<String, Object> invitation(long invitationId, long tripId) {
        return jdbc.sql("""
                SELECT id, trip_id, inviter_id, invitee_user_id, invite_code, status,
                       expires_at, accepted_at, rejected_at, canceled_at, created_at, updated_at
                FROM travel_invitations
                WHERE id = ? AND trip_id = ?
                """)
                .params(invitationId, tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "초대를 찾을 수 없습니다."));
    }

    private Map<String, Object> membership(long tripId, long userId, Long invitationId) {
        Map<String, Object> participantView = trips.members(tripId).stream()
                .filter(member -> RowSupport.longValue(member, "id") == userId)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "여행 참여 정보를 저장하지 못했습니다."));
        participantView = new LinkedHashMap<>(participantView);
        participantView.put("tripId", tripId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (invitationId != null) result.put("invitationId", invitationId);
        result.put("trip", trips.view(tripId));
        result.put("participant", participantView);
        return result;
    }

    private Map<String, Object> toInvitationView(Map<String, Object> row) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", RowSupport.longValue(row, "id"));
        value.put("tripId", RowSupport.longValue(row, "trip_id"));
        value.put("inviterId", RowSupport.longValue(row, "inviter_id"));
        putIfPresent(value, "inviterNickname", row, "inviter_nickname");
        putIfPresent(value, "inviteeId", row, "invitee_user_id");
        putIfPresent(value, "inviteeNickname", row, "invitee_nickname");
        value.put("code", RowSupport.strValue(row, "invite_code"));
        value.put("status", apiStatus(RowSupport.strValue(row, "status")));
        value.put("expiresAt", RowSupport.value(row, "expires_at"));
        putIfPresent(value, "acceptedAt", row, "accepted_at");
        putIfPresent(value, "declinedAt", row, "rejected_at");
        putIfPresent(value, "cancelledAt", row, "canceled_at");
        putIfPresent(value, "createdAt", row, "created_at");
        return value;
    }

    private String apiStatus(String status) {
        return switch (status) {
            case "REJECTED" -> "DECLINED";
            case "CANCELED", "EXPIRED" -> "CANCELLED";
            default -> status;
        };
    }

    private void putIfPresent(Map<String, Object> target, String key, Map<String, Object> row, String rowKey) {
        Object value = nullable(row, rowKey);
        if (value != null) target.put(key, value);
    }

    private Object nullable(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    public enum InvitationDecision {
        DECLINED,
        CANCELLED
    }

    private record JoinAttemptWindow(Instant startedAt, AtomicInteger count) {
    }
}
