package com.gayadi.server.coordination;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.coordination.dto.response.DateCoordinationResponse;
import com.gayadi.server.travel.dto.response.ParticipantResponse;
import com.gayadi.server.travel.dto.response.TripResponse;
import com.gayadi.server.travel.TripErrorCode;
import com.gayadi.server.travel.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 여행 날짜 조율 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class DateCoordinationService {

    private static final int MAX_FUTURE_YEARS = 2;

    private final DateCoordinationRepository repository;
    private final TripService trips;

    public DateCoordinationService(DateCoordinationRepository repository, TripService trips) {
        this.repository = repository;
        this.trips = trips;
    }

    /** 여행 날짜 조율 조건에 맞는 여행 날짜 조율 정보를 조회합니다. */
    public DateCoordinationResponse get(long actorId, long tripId) {
        trips.requireMember(tripId, actorId);
        return response(actorId, tripId);
    }

    /** 여행 날짜 조율 여행 날짜 조율 요청을 처리합니다. */
    @Transactional
    public DateCoordinationResponse submit(long actorId, long tripId, List<String> values) {
        lockTrip(tripId);
        trips.requireMember(tripId, actorId);
        repository.replaceAvailability(tripId, actorId, normalizeDates(values));
        return response(actorId, tripId);
    }

    /** 공통 가능 날짜를 검증해 여행 기간을 확정합니다. */
    @Transactional
    public DateCoordinationResponse finalizeDates(
            long actorId, long tripId, String startValue, String endValue) {
        lockTrip(tripId);
        trips.requireOwner(tripId, actorId);
        LocalDate start = AppDateFormat.parseDate(startValue, "여행 시작일");
        LocalDate end = AppDateFormat.parseDate(endValue, "여행 종료일");
        if (end.isBefore(start)) {
            throw new BusinessException(CoordinationErrorCode.COORDINATION_DATE_RANGE_INVALID);
        }
        int memberCount = repository.joinedMemberCount(tripId);
        if (memberCount > 1) {
            if (repository.submittedMemberCount(tripId) != memberCount) {
                throw new BusinessException(CoordinationErrorCode.COORDINATION_SUBMISSIONS_INCOMPLETE);
            }
            Set<LocalDate> common = new LinkedHashSet<>(repository.findCommonDates(tripId, memberCount));
            if (!common.containsAll(start.datesUntil(end.plusDays(1)).toList())) {
                throw new BusinessException(CoordinationErrorCode.COORDINATION_RANGE_NOT_COMMON);
            }
        }
        trips.finalizeDates(actorId, tripId, start, end);
        return response(actorId, tripId);
    }

    private DateCoordinationResponse response(long actorId, long tripId) {
        TripResponse trip = trips.view(tripId);
        List<ParticipantResponse> members = trips.members(tripId);
        Map<Long, List<String>> datesByUser = new LinkedHashMap<>();
        repository.findAvailability(tripId).forEach(result -> datesByUser
                .computeIfAbsent(result.userId(), ignored -> new java.util.ArrayList<>())
                .add(AppDateFormat.date(result.date())));
        Set<Long> submittedUsers = repository.findSubmittedUsers(tripId);
        int memberCount = members.size();
        List<String> common = repository.findCommonDates(tripId, memberCount).stream()
                .map(AppDateFormat::date).toList();
        boolean owner = trip.ownerId() == actorId;
        boolean canFinalize = owner
                && (memberCount == 1 || submittedUsers.size() == memberCount && !common.isEmpty());
        List<DateCoordinationResponse.ParticipantAvailability> participants = members.stream()
                .map(member -> {
                    long userId = member.userId();
                    return new DateCoordinationResponse.ParticipantAvailability(
                            userId, member.nickname(), member.characterKey(), submittedUsers.contains(userId),
                            datesByUser.getOrDefault(userId, List.of()));
                }).toList();
        return new DateCoordinationResponse(
                tripId, trip.startDate(), trip.endDate(), trip.version(), canFinalize, common, participants);
    }

    private List<LocalDate> normalizeDates(List<String> values) {
        if (values == null || values.isEmpty()) {
            throw new BusinessException(CoordinationErrorCode.COORDINATION_AVAILABILITY_REQUIRED);
        }
        LocalDate today = LocalDate.now();
        LocalDate maximum = today.plusYears(MAX_FUTURE_YEARS);
        Set<LocalDate> dates = new LinkedHashSet<>();
        for (String value : values) {
            LocalDate date = AppDateFormat.parseDate(value, "가능한 날짜");
            if (date.isBefore(today) || date.isAfter(maximum)) {
                throw new BusinessException(CoordinationErrorCode.COORDINATION_AVAILABILITY_DATE_OUT_OF_RANGE);
            }
            dates.add(date);
        }
        return dates.stream().sorted().toList();
    }

    private void lockTrip(long tripId) {
        if (!repository.lockTrip(tripId)) {
            throw new BusinessException(TripErrorCode.TRIP_NOT_FOUND);
        }
    }

}
