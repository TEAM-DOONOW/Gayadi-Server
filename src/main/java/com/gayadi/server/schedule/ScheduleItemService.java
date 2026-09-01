package com.gayadi.server.schedule;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.schedule.dto.response.ScheduleResponse;
import com.gayadi.server.schedule.model.ScheduleType;
import com.gayadi.server.schedule.query.EditableScheduleItemQueryResult;
import com.gayadi.server.schedule.query.ScheduleItemQueryResult;
import com.gayadi.server.schedule.query.ScheduleTripQueryResult;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.TripErrorCode;
import com.gayadi.server.travel.model.TripStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 여행 일정과 계획 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class ScheduleItemService {

    private final ScheduleRepository repository;
    private final TripService trips;

    public ScheduleItemService(ScheduleRepository repository, TripService trips) {
        this.repository = repository;
        this.trips = trips;
    }

    /** 일정 항목 조건에 맞는 일정 항목 정보를 조회합니다. */
    public List<ScheduleResponse> list(long userId, long tripId) {
        trips.requireMember(tripId, userId);
        return repository.findAllItems(tripId).stream()
                .map(this::toView)
                .toList();
    }

    /** 일정 항목 일정 항목 정보를 등록합니다. */
    @Transactional
    public ScheduleResponse create(long userId, long tripId, ScheduleCommand command) {
        ScheduleTripQueryResult trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);

        validateCommand(command);
        validateDate(trip, command.date());
        requirePlace(command.placeId(), tripId, userId);

        long planId = planForDate(trip, tripId, command.date(), userId);
        int sequence = nextSequence(planId);
        LocalDateTime start = LocalDateTime.of(command.date(), command.time());

        long itemId = repository.insertItem(
                planId,
                command.placeId(),
                command.title().trim(),
                sequence,
                start,
                plannedEnd(command),
                blankToNull(command.memo()),
                command.type());

        incrementPlanVersion(planId);
        expireTripRoutes(tripId);

        return item(tripId, itemId);
    }

    /** 일정 항목 일정 항목 상태를 변경합니다. */
    @Transactional
    public ScheduleResponse update(
            long userId,
            long tripId,
            long scheduleId,
            SchedulePatch patch) {
        ScheduleTripQueryResult trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);

        EditableScheduleItemQueryResult current = lockedItem(tripId, scheduleId);
        ScheduleCommand command = mergedCommand(current, patch);
        boolean visited = patch.isVisited() == null
                ? current.visited()
                : patch.isVisited();

        validateCommand(command);
        validateDate(trip, command.date());
        requirePlace(command.placeId(), tripId, userId);

        // 날짜가 바뀐 일정은 새 일차의 마지막 순번으로 이동한다.
        long oldPlanId = current.planId();
        long newPlanId = planForDate(trip, tripId, command.date(), userId);
        int sequence = current.sequenceNo();

        if (oldPlanId != newPlanId) {
            repository.reserveSequence(scheduleId);
            sequence = nextSequence(newPlanId);
        }

        LocalDateTime start = LocalDateTime.of(command.date(), command.time());
        repository.updateItem(
                scheduleId,
                newPlanId,
                command.placeId(),
                command.title().trim(),
                sequence,
                start,
                plannedEnd(command),
                blankToNull(command.memo()),
                command.type(),
                visited);

        incrementPlanVersion(newPlanId);

        if (oldPlanId != newPlanId) {
            normalize(oldPlanId);
            incrementPlanVersion(oldPlanId);
        }

        expireTripRoutes(tripId);

        return item(tripId, scheduleId);
    }

    /** 기존 내부 호출과 테스트가 전체 일정 값을 넘길 때 사용하는 호환 진입점이다. */
    @Transactional
    public ScheduleResponse update(
            long userId,
            long tripId,
            long scheduleId,
            ScheduleCommand command,
            boolean visited) {
        SchedulePatch patch = new SchedulePatch(
                command.title(),
                command.date(),
                command.time(),
                command.type(),
                command.placeId(),
                true,
                command.endTime(),
                true,
                command.memo(),
                visited);

        return update(userId, tripId, scheduleId, patch);
    }

    /** 일정 항목 일정 항목 정보를 삭제합니다. */
    @Transactional
    public void delete(long userId, long tripId, long scheduleId) {
        ScheduleTripQueryResult trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);

        EditableScheduleItemQueryResult current = lockedItem(tripId, scheduleId);
        long planId = current.planId();

        expireTripRoutes(tripId);
        repository.deleteItem(scheduleId);
        normalize(planId);
        incrementPlanVersion(planId);
    }

    /** 요청 순서에 맞춰 일정 항목을 재정렬합니다. */
    @Transactional
    public List<ScheduleResponse> reorder(long userId, long tripId, List<Long> scheduleIds) {
        ScheduleTripQueryResult trip = lockTrip(tripId);
        trips.requireMember(tripId, userId);
        requireEditableTrip(trip);

        if (scheduleIds == null || scheduleIds.isEmpty()) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_ORDER_REQUIRED);
        }

        Set<Long> unique = new LinkedHashSet<>(scheduleIds);
        if (unique.size() != scheduleIds.size()) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_ORDER_DUPLICATED);
        }

        Map<Long, Long> planByItem = repository.lockItemPlans(tripId);
        if (!planByItem.keySet().equals(unique)) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_ORDER_INCOMPLETE);
        }

        repository.reorder(tripId, scheduleIds, planByItem);
        expireTripRoutes(tripId);

        return list(userId, tripId);
    }

    private ScheduleTripQueryResult lockTrip(long tripId) {
        return repository.lockTrip(tripId)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
    }

    private void incrementPlanVersion(long planId) {
        repository.incrementPlanVersion(planId);
    }

    private void expireTripRoutes(long tripId) {
        repository.expireTripRoutes(tripId);
    }

    private ScheduleResponse item(long tripId, long itemId) {
        return repository.findItem(tripId, itemId)
                .map(this::toView)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }

    private ScheduleResponse toView(ScheduleItemQueryResult row) {
        LocalDateTime start = row.plannedStart() == null
                ? row.planDate().atStartOfDay()
                : row.plannedStart();
        int order = row.globalOrder() == null ? row.sequenceNo() - 1 : row.globalOrder();

        return new ScheduleResponse(
                row.id(),
                row.tripId(),
                row.title(),
                row.placeId(),
                row.placeName(),
                AppDateFormat.date(row.planDate()),
                AppDateFormat.time(start.toLocalTime()),
                row.plannedEnd() == null ? null : AppDateFormat.time(row.plannedEnd().toLocalTime()),
                row.memo(),
                row.type(),
                order,
                row.visited());
    }

    private EditableScheduleItemQueryResult lockedItem(long tripId, long itemId) {
        return repository.lockItem(tripId, itemId)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND));
    }

    private long planForDate(
            ScheduleTripQueryResult trip,
            long tripId,
            LocalDate date,
            long userId) {
        Long planId = repository.findPlanId(tripId, date).orElse(null);
        if (planId != null) {
            return planId;
        }

        LocalDate startDate = trip.startDate();
        int day = Math.toIntExact(ChronoUnit.DAYS.between(startDate, date)) + 1;
        var inserted = repository.insertManualPlan(tripId, date, day, userId);
        if (inserted.isPresent()) {
            return inserted.get();
        }

        return repository.findPlanId(tripId, date)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.SCHEDULE_PLAN_CREATION_CONFLICT));
    }

    private int nextSequence(long planId) {
        return repository.nextSequence(planId);
    }

    private void normalize(long planId) {
        repository.normalize(planId);
    }

    private void validateDate(ScheduleTripQueryResult trip, LocalDate date) {
        LocalDate start = trip.startDate();
        LocalDate end = trip.endDate();
        if (date.isBefore(start) || date.isAfter(end)) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_DATE_OUTSIDE_TRIP);
        }
    }

    private ScheduleCommand mergedCommand(EditableScheduleItemQueryResult current, SchedulePatch patch) {
        String title = patch.title() == null
                ? current.title()
                : patch.title();
        LocalDate date = patch.date() == null
                ? current.planDate()
                : patch.date();
        LocalTime currentTime = current.plannedStart() == null
                ? LocalTime.MIDNIGHT
                : current.plannedStart().toLocalTime();
        LocalTime time = patch.time() == null ? currentTime : patch.time();
        ScheduleType type = patch.type() == null
                ? current.type()
                : patch.type();
        Long placeId = patch.placeIdPresent()
                ? patch.placeId()
                : current.placeId();
        LocalTime currentEndTime = current.plannedEnd() == null ? null : current.plannedEnd().toLocalTime();
        LocalTime endTime = patch.endTimePresent() ? patch.endTime() : currentEndTime;
        String memo = patch.memo() == null ? current.memo() : patch.memo();
        return new ScheduleCommand(
                title,
                date,
                time,
                type,
                placeId,
                endTime,
                memo);
    }

    private void validateCommand(ScheduleCommand command) {
        if (command == null || command.title() == null || command.title().isBlank()
                || command.title().trim().length() > 200) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_TITLE_INVALID);
        }
        if (command.date() == null || command.time() == null || command.type() == null) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_REQUIRED_FIELDS_MISSING);
        }
        if (command.endTime() != null && !command.endTime().isAfter(command.time())) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_END_TIME_INVALID);
        }
        if (command.memo() != null && command.memo().trim().length() > 500) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_MEMO_TOO_LONG);
        }
    }

    private void requireEditableTrip(ScheduleTripQueryResult trip) {
        if (trip.status() == TripStatus.COMPLETED || trip.status() == TripStatus.CANCELED) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_TRIP_NOT_EDITABLE);
        }
    }

    private void requirePlace(Long placeId, long tripId, long userId) {
        if (placeId == null) {
            return;
        }
        if (!repository.placeExists(placeId, tripId, userId)) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_PLACE_NOT_FOUND);
        }
    }

    private LocalDateTime plannedEnd(ScheduleCommand command) {
        return command.endTime() == null
                ? null : LocalDateTime.of(command.date(), command.endTime());
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record ScheduleCommand(
            String title,
            LocalDate date,
            LocalTime time,
            ScheduleType type,
            Long placeId,
            LocalTime endTime,
            String memo
    ) {
        public ScheduleCommand(
                String title,
                LocalDate date,
                LocalTime time,
                ScheduleType type,
                Long placeId) {
            this(
                    title,
                    date,
                    time,
                    type,
                    placeId,
                    null,
                    "");
        }
    }

    public record SchedulePatch(
            String title,
            LocalDate date,
            LocalTime time,
            ScheduleType type,
            Long placeId,
            boolean placeIdPresent,
            LocalTime endTime,
            boolean endTimePresent,
            String memo,
            Boolean isVisited
    ) {
        public SchedulePatch(
                String title,
                LocalDate date,
                LocalTime time,
                ScheduleType type,
                Long placeId,
                boolean placeIdPresent,
                Boolean isVisited) {
            this(
                    title,
                    date,
                    time,
                    type,
                    placeId,
                    placeIdPresent,
                    null,
                    false,
                    null,
                    isVisited);
        }
    }
}
