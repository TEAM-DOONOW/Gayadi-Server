package com.gayadi.server.invitation;

import com.gayadi.server.invitation.model.InvitationDecision;
import com.gayadi.server.invitation.dto.response.InvitationResponse;
import com.gayadi.server.invitation.query.InvitationQueryResult;
import com.gayadi.server.invitation.query.InvitationJoinQueryResult;
import com.gayadi.server.invitation.query.InvitationTripQueryResult;
import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.dto.response.MembershipResponse;
import com.gayadi.server.travel.dto.response.ParticipantResponse;
import com.gayadi.server.travel.TripErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Locale;

/** 여행 초대 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class InvitationService {

    private static final char[] CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int CODE_GENERATION_ATTEMPTS = 20;
    private static final int MAX_JOIN_ATTEMPTS_PER_WINDOW = 20;
    private static final Duration JOIN_ATTEMPT_WINDOW = Duration.ofMinutes(10);

    private final TripService trips;
    private final UserService users;
    private final InvitationRepository repository;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<Long, JoinAttemptWindow> joinAttempts = new ConcurrentHashMap<>();

    public InvitationService(
            TripService trips,
            UserService users,
            InvitationRepository repository) {
        this.trips = trips;
        this.users = users;
        this.repository = repository;
    }

    /** 여행의 초대 목록을 페이지 조건에 맞춰 조회합니다. */
    @Transactional
    public List<InvitationResponse> list(long tripId, long userId, int requestedLimit, int requestedOffset) {
        trips.requireMember(tripId, userId);
        repository.expirePending(tripId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        int offset = Math.max(0, requestedOffset);
        return repository.findAll(tripId, limit, offset).stream()
                .map(this::toInvitationView)
                .toList();
    }

    /** 초대 대상과 만료 정책을 검증해 여행 초대를 생성합니다. */
    @Transactional
    public InvitationResponse create(
            long tripId,
            long userId,
            Long inviteeUserId,
            LocalDateTime expiresAt) {
        lockUsersInOrder(userId, inviteeUserId);
        InvitationTripQueryResult trip = lockTrip(tripId);
        trips.requireOwner(tripId, userId);
        if (trip.status() != com.gayadi.server.travel.model.TripStatus.PLANNING) {
            throw new BusinessException(InvitationErrorCode.INVITATION_TRIP_NOT_PLANNING);
        }

        if (inviteeUserId != null) {
            if (repository.isJoinedMember(tripId, inviteeUserId)) {
                throw new BusinessException(InvitationErrorCode.INVITEE_ALREADY_MEMBER);
            }
        }

        LocalDateTime expiration = expiresAt == null ? LocalDateTime.now().plusDays(7) : expiresAt;
        if (!expiration.isAfter(LocalDateTime.now())) {
            throw new BusinessException(InvitationErrorCode.INVITATION_EXPIRATION_INVALID);
        }

        long invitationId;
        try {
            invitationId = repository.create(
                    tripId, userId, inviteeUserId, availableCode(), expiration);
        } catch (org.springframework.dao.DuplicateKeyException exception) {
            throw new BusinessException(InvitationErrorCode.INVITATION_CODE_UNAVAILABLE);
        }
        return toInvitationView(repository.find(invitationId, tripId)
                .orElseThrow(() -> new BusinessException(InvitationErrorCode.INVITATION_NOT_FOUND)));
    }

    /** 상태 여행 초대 상태를 변경합니다. */
    @Transactional
    public InvitationResponse updateStatus(
            long tripId,
            long invitationId,
            long userId,
            InvitationDecision decision) {
        InvitationJoinQueryResult current = repository.findJoinCandidate(invitationId, tripId)
                .orElseThrow(() -> new BusinessException(InvitationErrorCode.INVITATION_NOT_FOUND));
        if (decision == InvitationDecision.CANCELLED) {
            trips.requireOwner(tripId, userId);
        } else {
            Long invitee = current.inviteeUserId();
            if (invitee == null || invitee != userId) {
                throw new BusinessException(InvitationErrorCode.INVITATION_DECLINE_FORBIDDEN);
            }
        }

        String status = decision == InvitationDecision.CANCELLED ? "CANCELED" : "REJECTED";
        String timeColumn = decision == InvitationDecision.CANCELLED ? "canceled_at" : "rejected_at";
        if (!repository.updateDecision(invitationId, tripId, status, timeColumn)) {
            throw new BusinessException(InvitationErrorCode.INVITATION_STATUS_NOT_PENDING);
        }
        return toInvitationView(repository.find(invitationId, tripId)
                .orElseThrow(() -> new BusinessException(InvitationErrorCode.INVITATION_NOT_FOUND)));
    }

    /** 수락된 초대로 사용자를 여행 참여자로 등록합니다. */
    @Transactional
    public MembershipResponse join(
            long userId,
            String inviteCode,
            Long departurePlaceId,
            Long returnPlaceId) {
        users.lockActive(userId);
        checkJoinAttemptLimit(userId);
        String normalizedCode = normalizeCode(inviteCode);

        InvitationJoinQueryResult current = repository.findJoinCandidateByCode(normalizedCode)
                .orElse(null);

        if (current == null) {
            return joinByReusableTripCode(
                    userId, normalizedCode, departurePlaceId, returnPlaceId);
        }

        long invitationId = current.id();
        long tripId = current.tripId();
        InvitationTripQueryResult lockedTrip = lockTrip(tripId);
        validateJoinableInvitation(current, userId);
        validateMemberCapacity(lockedTrip, tripId);

        if (repository.isJoinedMember(tripId, userId)) {
            throw new BusinessException(TripErrorCode.TRIP_ALREADY_JOINED);
        }

        if (!repository.accept(userId, invitationId, normalizedCode)) {
            throw new BusinessException(InvitationErrorCode.INVITATION_CODE_USED_OR_EXPIRED);
        }

        trips.addMember(
                tripId,
                new TripService.AddMember(
                        userId,
                        departurePlaceId,
                        returnPlaceId));

        trips.requireMember(tripId, userId);
        return membership(tripId, userId, invitationId);
    }

    private MembershipResponse joinByReusableTripCode(
            long userId,
            String inviteCode,
            Long departurePlaceId,
            Long returnPlaceId) {
        Long tripId = repository.findTripIdByReusableCode(inviteCode)
                .orElseThrow(() -> new BusinessException(InvitationErrorCode.INVITATION_CODE_NOT_FOUND));

        InvitationTripQueryResult lockedTrip = lockTrip(tripId);
        validateMemberCapacity(lockedTrip, tripId);
        if (repository.isJoinedMember(tripId, userId)) {
            throw new BusinessException(TripErrorCode.TRIP_ALREADY_JOINED);
        }
        trips.addMember(
                tripId,
                new TripService.AddMember(
                        userId,
                        departurePlaceId,
                        returnPlaceId));
        return membership(tripId, userId, null);
    }

    private InvitationTripQueryResult lockTrip(long tripId) {
        return repository.lockTrip(tripId)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
    }

    private void validateJoinableInvitation(InvitationJoinQueryResult invitation, long userId) {
        String status = invitation.databaseStatus();
        LocalDateTime expiresAt = invitation.expiresAt();
        if (!"PENDING".equals(status) || !expiresAt.isAfter(LocalDateTime.now())) {
            throw new BusinessException(InvitationErrorCode.INVITATION_CODE_USED_OR_EXPIRED);
        }
        Long invitee = invitation.inviteeUserId();
        if (invitee != null && invitee != userId) {
            throw new BusinessException(InvitationErrorCode.INVITATION_CODE_FORBIDDEN);
        }
    }

    private void validateMemberCapacity(InvitationTripQueryResult trip, long tripId) {
        if (trip.status() != com.gayadi.server.travel.model.TripStatus.PLANNING) {
            throw new BusinessException(InvitationErrorCode.INVITATION_TRIP_NOT_JOINABLE);
        }
        Integer maxMembers = trip.maxMembers();
        if (maxMembers == null) {
            return;
        }
        int joined = repository.countJoinedMembers(tripId);
        if (joined >= maxMembers) {
            throw new BusinessException(TripErrorCode.TRIP_MEMBER_CAPACITY_REACHED);
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

    private String availableCode() {
        for (int attempt = 0; attempt < CODE_GENERATION_ATTEMPTS; attempt++) {
            // 여행의 재사용 코드(G...)와 겹치지 않도록 개별 초대는 I로 시작한다.
            StringBuilder code = new StringBuilder("I");
            for (int index = 1; index < CODE_LENGTH; index++) {
                code.append(CODE_CHARACTERS[random.nextInt(CODE_CHARACTERS.length)]);
            }
            if (!repository.codeExists(code.toString())) {
                return code.toString();
            }
        }
        throw new BusinessException(InvitationErrorCode.INVITATION_CODE_UNAVAILABLE);
    }

    private String normalizeCode(String inviteCode) {
        return inviteCode == null ? "" : inviteCode.trim().toUpperCase(Locale.ROOT);
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
            throw new BusinessException(InvitationErrorCode.INVITATION_JOIN_RATE_LIMITED);
        }
        if (joinAttempts.size() > 10000) {
            joinAttempts.entrySet().removeIf(entry ->
                    entry.getValue().startedAt().plus(JOIN_ATTEMPT_WINDOW).isBefore(now));
        }
    }

    private MembershipResponse membership(long tripId, long userId, Long invitationId) {
        ParticipantResponse participant = trips.members(tripId).stream()
                .filter(member -> member.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("저장한 여행 참여 정보를 다시 조회하지 못했습니다."));
        ParticipantResponse participantWithTrip = new ParticipantResponse(
                participant.id(),
                participant.userId(),
                participant.participantId(),
                participant.nickname(),
                participant.characterKey(),
                participant.role(),
                participant.status(),
                participant.departurePlaceId(),
                participant.returnPlaceId(),
                tripId);
        return new MembershipResponse(
                invitationId,
                trips.view(tripId),
                participantWithTrip);
    }

    private InvitationResponse toInvitationView(InvitationQueryResult result) {
        return new InvitationResponse(
                result.id(), result.tripId(), result.inviterId(), result.inviterNickname(),
                result.inviteeId(), result.inviteeNickname(), result.code(), result.status(),
                result.expiresAt(), result.acceptedAt(), result.declinedAt(),
                result.cancelledAt(), result.createdAt());
    }

    private record JoinAttemptWindow(
            Instant startedAt,
            AtomicInteger count
    ) {
    }
}
