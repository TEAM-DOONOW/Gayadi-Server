package com.gayadi.server.travel;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.travel.model.DepartureMode;
import com.gayadi.server.travel.model.TripStatus;
import com.gayadi.server.travel.dto.response.ParticipantResponse;
import com.gayadi.server.travel.dto.response.TripResponse;
import com.gayadi.server.travel.query.ParticipantQueryResult;
import com.gayadi.server.travel.query.TripQueryResult;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 여행과 참여자 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class TripService {

    private static final int DEFAULT_MAX_MEMBERS = 20;
    private static final int MAX_LIST_SIZE = 100;
    private static final char[] INVITE_CODE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int INVITE_CODE_ATTEMPTS = 20;

    private final TripRepository repository;
    private final UserService users;
    private final SecureRandom random = new SecureRandom();

    public TripService(TripRepository repository, UserService users) {
        this.repository = repository;
        this.users = users;
    }

    /** 여행 여행 정보를 등록합니다. */
    @Transactional
    public TripResponse create(CreateTrip command) {
        users.lockActive(command.ownerId());
        validateTitle(command.title());
        validateDates(command.startDate(), command.endDate());
        validateDeparture(command.departureMode(), command.meetingAt(), command.meetingPlaceId());
        validateMaxMembers(command.maxMembers());
        long tripId = insertTrip(command);
        requireReachablePlace(tripId, command.ownerId(), command.meetingPlaceId());
        validateMemberPlaces(tripId, command.ownerId(),
                command.ownerDeparturePlaceId(), command.ownerReturnPlaceId());
        addMemberInternal(tripId, command.ownerId(), "OWNER",
                command.ownerDeparturePlaceId(), command.ownerReturnPlaceId());
        return view(tripId);
    }

    /** 사용자를 소유자와 첫 참여자로 포함해 여행을 생성합니다. */
    @Transactional
    public TripResponse createForUser(
            long ownerId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            List<String> cities) {
        users.lockActive(ownerId);
        validateDates(startDate, endDate);
        validateTitle(title);
        List<String> normalizedCities = normalizeCities(cities);
        long regionId = resolveRegion(normalizedCities.getFirst());
        long tripId = insertTrip(new CreateTrip(
                ownerId, title.trim(), startDate, endDate, DepartureMode.SEPARATE,
                null, null, regionId, null, DEFAULT_MAX_MEMBERS, null, null));
        addMemberInternal(tripId, ownerId, "OWNER", null, null);
        replaceCities(tripId, normalizedCities);
        return view(tripId);
    }

    /** 참여자 여행 정보를 등록합니다. */
    @Transactional
    public ParticipantResponse addMember(long tripId, AddMember command) {
        users.lockActive(command.userId());
        lockTrip(tripId);
        ensureMemberCanJoin(tripId);
        validateMemberPlaces(tripId, command.userId(), command.departurePlaceId(), command.returnPlaceId());
        addMemberInternal(tripId, command.userId(), "MEMBER",
                command.departurePlaceId(), command.returnPlaceId());
        return memberByUser(tripId, command.userId());
    }

    /** 소유자 권한을 확인하고 여행 참여자를 추가합니다. */
    @Transactional
    public ParticipantResponse addMemberAsOwner(long actorId, long tripId, AddMember command) {
        lockUsersInOrder(actorId, command.userId());
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        ensureMemberCanJoin(tripId);
        validateMemberPlaces(tripId, command.userId(), command.departurePlaceId(), command.returnPlaceId());
        addMemberInternal(tripId, command.userId(), "MEMBER",
                command.departurePlaceId(), command.returnPlaceId());
        return memberByUser(tripId, command.userId());
    }

    /** 참여자 여행 정보를 삭제합니다. */
    @Transactional
    public void removeMember(long actorId, long tripId, long userId) {
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        if (actorId == userId) {
            throw new BusinessException(TripErrorCode.TRIP_OWNER_REMOVAL_FORBIDDEN);
        }
        if (!repository.removeParticipant(tripId, userId)) {
            throw new BusinessException(TripErrorCode.TRIP_MEMBER_NOT_FOUND);
        }
    }

    /** 여행 상세 정보를 조회합니다. */
    public TripResponse view(long tripId) {
        TripQueryResult trip = tripResult(tripId);
        return toView(trip, repository.findCities(tripId), repository.findJoinedUserIds(tripId));
    }

    /** 사용자가 참여한 여행 목록을 상태와 페이지 조건으로 조회합니다. */
    public List<TripResponse> listForUser(
            long userId,
            String status,
            int requestedLimit,
            int offset) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIST_SIZE));
        int safeOffset = Math.max(0, offset);

        TripStatus normalizedStatus = status == null || status.isBlank()
                ? null
                : normalizeStatus(status);

        List<TripQueryResult> rows = repository.findAllForUser(
                userId,
                normalizedStatus,
                limit,
                safeOffset);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 목록 전체의 연관 정보를 일괄 조회해 여행별 반복 쿼리를 방지합니다.
        List<Long> tripIds = rows.stream()
                .map(TripQueryResult::id)
                .toList();
        Map<Long, List<String>> citiesByTrip = repository.findCitiesByTripIds(tripIds);
        Map<Long, List<Long>> membersByTrip = repository.findJoinedUserIdsByTripIds(tripIds);

        return rows.stream()
                .map(row -> {
                    long id = row.id();

                    return toView(
                            row,
                            citiesByTrip.getOrDefault(id, List.of()),
                            membersByTrip.getOrDefault(id, List.of()));
                })
                .toList();
    }

    /** 여행 참여자 목록을 조회합니다. */
    public List<ParticipantResponse> members(long tripId) {
        requireTrip(tripId);
        return repository.findParticipants(tripId).stream()
                .map(this::toParticipant)
                .toList();
    }

    /** 여행 소유자가 여행 기본 정보를 수정합니다. */
    @Transactional
    public TripResponse update(
            long actorId,
            long tripId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            List<String> cities,
            Integer expectedVersion) {
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        validateDates(startDate, endDate);
        validateTitle(title);
        requirePlansWithinRange(tripId, startDate, endDate);
        List<String> normalizedCities = normalizeCities(cities);
        long regionId = resolveRegion(normalizedCities.getFirst());
        if (!repository.updateTrip(
                tripId, title.trim(), startDate, endDate, regionId, expectedVersion)) {
            throw new BusinessException(TripErrorCode.TRIP_VERSION_CONFLICT);
        }
        replaceCities(tripId, normalizedCities);
        recalculatePlanDays(tripId, startDate);
        return view(tripId);
    }

    /** 날짜 조율 결과를 여행의 최종 기간으로 확정합니다. */
    @Transactional
    public TripResponse finalizeDates(
            long actorId, long tripId, LocalDate startDate, LocalDate endDate) {
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        validateDates(startDate, endDate);
        requirePlansWithinRange(tripId, startDate, endDate);
        repository.updateDates(tripId, startDate, endDate);
        recalculatePlanDays(tripId, startDate);
        return view(tripId);
    }

    private void requirePlansWithinRange(long tripId, LocalDate startDate, LocalDate endDate) {
        if (repository.plansExistOutside(tripId, startDate, endDate)) {
            throw new BusinessException(TripErrorCode.TRIP_SCHEDULE_OUTSIDE_DATE_RANGE);
        }
    }

    /** 여행 상태를 검증된 상태 값으로 변경합니다. */
    @Transactional
    public TripResponse changeStatus(long actorId, long tripId, TripStatus target) {
        TripQueryResult trip = lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        TripStatus current = trip.status();
        if (!allowedTransition(current, target)) {
            throw new BusinessException(TripErrorCode.TRIP_STATUS_TRANSITION_INVALID,
                    statusLabel(current), statusLabel(target));
        }
        repository.updateStatus(tripId, target);
        return view(tripId);
    }

    /** 문자열 상태 값을 변환하여 여행 상태를 변경합니다. */
    @Transactional
    public TripResponse changeStatus(long actorId, long tripId, String target) {
        return changeStatus(actorId, tripId, normalizeStatus(target));
    }

    /** 여행 소유자가 여행을 삭제 처리합니다. */
    @Transactional
    public void delete(long actorId, long tripId) {
        lockTrip(tripId);
        requireOwnerRow(tripId, actorId);
        if (!repository.cancel(tripId)) {
            throw new BusinessException(TripErrorCode.TRIP_NOT_FOUND);
        }
    }

    /** 여행을 진행 중 상태로 시작합니다. */
    @Transactional
    public TripResponse start(long tripId) {
        return transitionWithoutActor(tripId, TripStatus.PLANNING, TripStatus.IN_PROGRESS);
    }

    /** 여행을 완료 상태로 종료합니다. */
    @Transactional
    public TripResponse complete(long tripId) {
        return transitionWithoutActor(tripId, TripStatus.IN_PROGRESS, TripStatus.COMPLETED);
    }

    /** 존재하는 여행인지 확인하고 조회 결과를 반환합니다. */
    public TripQueryResult requireTrip(long tripId) {
        return tripResult(tripId);
    }

    /** 사용자가 여행 소유자인지 확인합니다. */
    public void requireOwner(long tripId, long userId) {
        requireTrip(tripId);
        if (!repository.isOwner(tripId, userId)) {
            throw new BusinessException(TripErrorCode.TRIP_OWNER_REQUIRED);
        }
    }

    /** 사용자가 여행 참여자인지 확인합니다. */
    public void requireMember(long tripId, long userId) {
        if (!repository.isMember(tripId, userId)) {
            throw new BusinessException(TripErrorCode.TRIP_MEMBER_REQUIRED);
        }
    }

    private TripResponse transitionWithoutActor(long tripId, TripStatus current, TripStatus target) {
        if (!repository.transition(tripId, current, target)) {
            throw new BusinessException(TripErrorCode.TRIP_STATUS_CHANGE_CONFLICT);
        }
        return view(tripId);
    }

    private long insertTrip(CreateTrip command) {
        for (int attempt = 0; attempt < INVITE_CODE_ATTEMPTS; attempt++) {
            String inviteCode = availableTripCode();
            var inserted = repository.insert(command, DEFAULT_MAX_MEMBERS, inviteCode);
            if (inserted.isPresent()) {
                return inserted.getAsLong();
            }
            // 드문 코드 충돌이면 저장점으로 복구된 같은 트랜잭션에서 새 코드를 만든다.
        }
        throw new BusinessException(TripErrorCode.TRIP_INVITE_CODE_UNAVAILABLE);
    }

    private TripQueryResult lockTrip(long tripId) {
        return repository.lock(tripId)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
    }

    private void requireOwnerRow(long tripId, long userId) {
        if (!repository.isOwner(tripId, userId)) {
            throw new BusinessException(TripErrorCode.TRIP_OWNER_REQUIRED);
        }
    }

    private void ensureMemberCanJoin(long tripId) {
        TripQueryResult trip = tripResult(tripId);
        if (trip.status() != TripStatus.PLANNING) {
            throw new BusinessException(TripErrorCode.TRIP_NOT_JOINABLE);
        }
        int maxMembers = trip.maxMembers() == null ? DEFAULT_MAX_MEMBERS : trip.maxMembers();
        long current = repository.countJoinedMembers(tripId);
        if (current >= maxMembers) {
            throw new BusinessException(TripErrorCode.TRIP_MEMBER_CAPACITY_REACHED);
        }
    }

    private void addMemberInternal(long tripId, long userId, String role,
                                   Long departurePlaceId, Long returnPlaceId) {
        if (repository.restoreParticipant(
                tripId, userId, role, departurePlaceId, returnPlaceId)) {
            repository.expireParticipantRoutes(tripId, userId);
            return;
        }
        try {
            repository.insertParticipant(tripId, userId, role, departurePlaceId, returnPlaceId);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(TripErrorCode.TRIP_ALREADY_JOINED);
        }
    }

    private ParticipantResponse memberByUser(long tripId, long userId) {
        return repository.findParticipant(tripId, userId).map(this::toParticipant)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_MEMBER_NOT_FOUND));
    }

    private TripResponse toView(
            TripQueryResult row,
            List<String> cities,
            List<?> members) {
        String status = row.status().name();
        List<Long> participantIds = members.stream()
                .map(member -> {
                    if (member instanceof ParticipantResponse participant) {
                        return participant.userId();
                    }
                    if (member instanceof Number number) {
                        return number.longValue();
                    }
                    throw new IllegalArgumentException("지원하지 않는 참여자 조회 타입입니다.");
                })
                .toList();
        return new TripResponse(
                row.id(), row.title(), AppDateFormat.date(row.startDate()), AppDateFormat.date(row.endDate()), cities,
                "IN_PROGRESS".equals(status) ? "ONGOING" : status,
                row.ownerId(), participantIds, row.inviteCode() == null ? "" : row.inviteCode(),
                row.version(), row.createdAt(), row.updatedAt());
    }

    private ParticipantResponse toParticipant(ParticipantQueryResult row) {
        long userId = row.userId();
        return new ParticipantResponse(
                userId, userId, row.participantId(), row.nickname(), row.characterKey(),
                row.role(), row.status(), row.departurePlaceId(), row.returnPlaceId(), row.tripId());
    }

    private TripQueryResult tripResult(long tripId) {
        return repository.find(tripId)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
    }

    private List<String> normalizeCities(List<String> cities) {
        if (cities == null) {
            throw new BusinessException(TripErrorCode.TRIP_CITY_REQUIRED);
        }
        List<String> normalized = cities.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .limit(10)
                .toList();
        if (normalized.isEmpty()) {
            throw new BusinessException(TripErrorCode.TRIP_CITY_REQUIRED);
        }
        return normalized;
    }

    private long resolveRegion(String name) {
        Long regionId = repository.findRegionId(name).orElse(null);
        if (regionId != null) {
            return regionId;
        }

        // PostgreSQL은 유일 제약 오류가 난 트랜잭션에서 재조회할 수 없으므로
        // 기준 지역 행을 잠가 새로운 지역 등록을 짧게 직렬화한다.
        repository.lockRegionSequence();
        regionId = repository.findRegionId(name).orElse(null);
        if (regionId != null) {
            return regionId;
        }

        repository.insertRegion(name);
        return repository.findRegionId(name)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_REGION_CREATION_CONFLICT));
    }

    private void replaceCities(long tripId, List<String> cities) {
        repository.replaceCities(tripId, cities);
    }

    private void recalculatePlanDays(long tripId, LocalDate startDate) {
        repository.recalculatePlanDays(tripId, startDate);
        /* Repository가 잠금과 일차 재계산을 원자적으로 처리한다.


        // 같은 여행의 일차 유일 제약과 부딪치지 않도록 기존 값을 먼저 임시 범위로 옮긴다.
        */
    }

    private String availableTripCode() {
        for (int attempt = 0; attempt < INVITE_CODE_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder("G");
            for (int index = 1; index < 6; index++) {
                code.append(INVITE_CODE_CHARACTERS[random.nextInt(INVITE_CODE_CHARACTERS.length)]);
            }
            if (!repository.codeExists(code.toString())) {
                return code.toString();
            }
        }
        throw new BusinessException(TripErrorCode.TRIP_INVITE_CODE_UNAVAILABLE);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new BusinessException(TripErrorCode.TRIP_DATE_RANGE_INVALID);
        }
        if (startDate.plusDays(30).isBefore(endDate)) {
            throw new BusinessException(TripErrorCode.TRIP_DURATION_EXCEEDED);
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > 100) {
            throw new BusinessException(TripErrorCode.TRIP_TITLE_INVALID);
        }
    }

    private void validateMemberPlaces(
            long tripId, long userId, Long departurePlaceId, Long returnPlaceId) {
        requireReachablePlace(tripId, userId, departurePlaceId);
        requireReachablePlace(tripId, userId, returnPlaceId);
    }

    private void requireReachablePlace(long tripId, long userId, Long placeId) {
        if (placeId == null) {
            return;
        }
        if (!repository.isReachablePlace(tripId, userId, placeId)) {
            throw new BusinessException(TripErrorCode.TRIP_PLACE_INVALID);
        }
    }

    private void validateDeparture(DepartureMode mode, LocalDateTime meetingAt, Long meetingPlaceId) {
        if (mode == null) {
            throw new BusinessException(TripErrorCode.TRIP_DEPARTURE_MODE_REQUIRED);
        }
        if (mode == DepartureMode.TOGETHER && (meetingAt == null || meetingPlaceId == null)) {
            throw new BusinessException(TripErrorCode.TRIP_MEETING_REQUIRED);
        }
    }

    private void validateMaxMembers(Integer maxMembers) {
        if (maxMembers != null && (maxMembers < 1 || maxMembers > 100)) {
            throw new BusinessException(TripErrorCode.TRIP_MAX_MEMBERS_INVALID);
        }
    }

    private void lockUsersInOrder(long firstUserId, long secondUserId) {
        users.lockActive(Math.min(firstUserId, secondUserId));
        if (firstUserId != secondUserId) {
            users.lockActive(Math.max(firstUserId, secondUserId));
        }
    }

    private TripStatus normalizeStatus(String status) {
        try {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if ("ONGOING".equals(normalized)) {
                normalized = "IN_PROGRESS";
            }
            if ("CANCELED".equals(normalized)) {
                throw new IllegalArgumentException("취소 상태는 삭제 API로 처리합니다.");
            }
            return TripStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(TripErrorCode.TRIP_STATUS_INVALID);
        }
    }

    private boolean allowedTransition(TripStatus current, TripStatus target) {
        return (current == TripStatus.PLANNING && (target == TripStatus.IN_PROGRESS || target == TripStatus.CANCELED))
                || (current == TripStatus.IN_PROGRESS && (target == TripStatus.COMPLETED || target == TripStatus.CANCELED));
    }

    private String statusLabel(TripStatus status) {
        return switch (status) {
            case PLANNING -> "준비 중";
            case IN_PROGRESS -> "여행 중";
            case COMPLETED -> "완료";
            case CANCELED -> "취소";
        };
    }

    public record CreateTrip(
            long ownerId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            DepartureMode departureMode,
            LocalDateTime meetingAt,
            Long meetingPlaceId,
            long regionId,
            String tripPreferences,
            Integer maxMembers,
            Long ownerDeparturePlaceId,
            Long ownerReturnPlaceId
    ) {
    }

    public record AddMember(
            long userId,
            Long departurePlaceId,
            Long returnPlaceId
    ) {
    }
}
