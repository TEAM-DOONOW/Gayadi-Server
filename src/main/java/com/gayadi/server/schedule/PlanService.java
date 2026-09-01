package com.gayadi.server.schedule;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.schedule.PlanRepository.GeneratedPlanItem;
import com.gayadi.server.schedule.dto.response.PlanDayResponse;
import com.gayadi.server.schedule.dto.response.PlanItemResponse;
import com.gayadi.server.schedule.dto.response.PlanResponse;
import com.gayadi.server.schedule.query.*;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.survey.dto.response.GroupPersonalityResponse;
import com.gayadi.server.travel.TripErrorCode;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.model.TripStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/** 여행 일정과 계획 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class PlanService {
    private static final int MAX_PLACES_PER_DAY = 3;
    private static final int MAX_GENERATED_DAYS = 366;

    private final PlanRepository repository;
    private final TripService trips;
    private final SurveyService surveys;
    private final JsonSupport json;

    public PlanService(PlanRepository repository, TripService trips,
                       SurveyService surveys, JsonSupport json) {
        this.repository = repository;
        this.trips = trips;
        this.surveys = surveys;
        this.json = json;
    }

    /** 여행 기간과 장소 후보를 바탕으로 일차별 계획을 생성합니다. */
    @Transactional
    public PlanResponse generate(long tripId) {
        PlanTripQueryResult trip = repository.lockTrip(tripId)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
        if (trip.status() != TripStatus.PLANNING) {
            throw new BusinessException(ScheduleErrorCode.PLAN_GENERATION_TRIP_NOT_PLANNING);
        }
        GroupPersonalityResponse profile = surveys.groupProfile(tripId);
        ProfileCode profileCode = ProfileCode.from(profile.dominantProfile());
        long dayCount = ChronoUnit.DAYS.between(trip.startDate(), trip.endDate()) + 1;
        if (dayCount <= 0) {
            throw new BusinessException(ScheduleErrorCode.PLAN_TRIP_DATE_INVALID);
        }
        if (dayCount > MAX_GENERATED_DAYS) {
            throw new BusinessException(ScheduleErrorCode.PLAN_GENERATION_RANGE_EXCEEDED);
        }

        // 여행 기간과 성향에 맞는 후보를 한 번 조회해 모든 일차에 순환 배치합니다.
        int candidateLimit = Math.min(1_100,
                Math.max(MAX_PLACES_PER_DAY, Math.toIntExact(dayCount) * MAX_PLACES_PER_DAY));
        List<PlanPlaceQueryResult> places = repository.findCandidates(
                tripId, trip.regionId(), profileCode.place(), profileCode.energy(),
                profileCode.preparation(), candidateLimit);
        if (places.isEmpty()) {
            throw new BusinessException(ScheduleErrorCode.PLAN_PLACE_CANDIDATE_NOT_FOUND);
        }

        Map<Integer, Long> planIdByDay = repository.findPlanIdsByDay(tripId);
        repository.deleteItems(new ArrayList<>(planIdByDay.values()));
        String snapshot = json.write(profile);
        List<GeneratedPlanItem> items = new ArrayList<>();

        // 기존 일차 계획은 유지·갱신하고 부족한 일차만 생성해 식별자 변경을 줄입니다.
        try {
            for (int dayIndex = 0; dayIndex < Math.toIntExact(dayCount); dayIndex++) {
                int day = dayIndex + 1;
                LocalDate date = trip.startDate().plusDays(dayIndex);
                Long planId = planIdByDay.get(day);
                if (planId == null) {
                    planId = repository.insertPlan(tripId, date, day, day + "일차 일정",
                            trip.ownerId(), snapshot);
                    planIdByDay.put(day, planId);
                } else {
                    repository.updatePlan(planId, tripId, date, day + "일차 일정", snapshot);
                }
                items.addAll(itemsForDay(planId, date, dayIndex, places, profileCode));
            }
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ScheduleErrorCode.PLAN_GENERATION_CONFLICT);
        }

        // 일차별 조립이 끝난 후 남은 계획을 정리하고 항목을 일괄 저장합니다.
        repository.deletePlansAfterDay(tripId, Math.toIntExact(dayCount));
        repository.insertItems(items);
        return get(tripId);
    }

    /** 여행의 일차별 계획과 일정 항목을 조회합니다. */
    public PlanResponse get(long tripId) {
        trips.requireTrip(tripId);
        List<PlanDayQueryResult> days = repository.findDays(tripId);
        if (days.isEmpty()) {
            throw new BusinessException(ScheduleErrorCode.PLAN_NOT_FOUND);
        }
        List<Long> planIds = days.stream()
                .map(PlanDayQueryResult::id)
                .toList();
        Map<Long, List<PlanItemQueryResult>> items = repository.findItems(planIds).stream()
                .collect(Collectors.groupingBy(
                        PlanItemQueryResult::planId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        List<PlanDayResponse> responses = days.stream()
                .map(day -> new PlanDayResponse(
                        day.id(),
                        day.tripId(),
                        day.planDate(),
                        day.dayNumber(),
                        day.title(),
                        day.description(),
                        day.sourceType(),
                        day.status(),
                        day.preferenceSnapshot(),
                        day.createdBy(),
                        day.version(),
                        day.createdAt(),
                        day.updatedAt(),
                        items.getOrDefault(day.id(), List.of()).stream()
                                .map(this::toItem)
                                .toList()))
                .toList();

        PlanDayResponse first = responses.getFirst();
        return new PlanResponse(
                first.id(),
                first.trip_id(),
                first.plan_date(),
                first.day_number(),
                first.title(),
                first.description(),
                first.source_type(),
                first.status(),
                first.preference_snapshot(),
                first.created_by(),
                first.version(),
                first.created_at(),
                first.updated_at(),
                first.items(),
                responses);
    }

    /** 여행 일정의 첫 장소를 조회합니다. */
    public PlanPlaceQueryResult firstPlace(long tripId) {
        return boundaryPlace(tripId, false);
    }

    /** 여행 일정의 마지막 장소를 조회합니다. */
    public PlanPlaceQueryResult lastPlace(long tripId) {
        return boundaryPlace(tripId, true);
    }

    private PlanPlaceQueryResult boundaryPlace(long tripId, boolean descending) {
        return repository.findBoundaryPlace(tripId, descending)
                .orElseThrow(() -> new BusinessException(ScheduleErrorCode.PLAN_PLACE_REQUIRED));
    }

    private List<GeneratedPlanItem> itemsForDay(
            long planId,
            LocalDate date,
            int dayIndex,
            List<PlanPlaceQueryResult> places,
            ProfileCode profile) {
        int count = Math.min(MAX_PLACES_PER_DAY, places.size());
        int startHour = profile.preparation() == 'P' ? 9 : 10;
        int startMinute = profile.preparation() == 'P' ? 30 : 0;
        int duration = profile.energy() == 'A' ? 90 : 120;
        int interval = profile.energy() == 'A' ? 120 : 150;
        int offset = Math.floorMod(dayIndex * count, places.size());
        List<GeneratedPlanItem> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            PlanPlaceQueryResult place = places.get((offset + index) % places.size());
            LocalDateTime start = date.atTime(startHour, startMinute).plusMinutes((long) index * interval);
            result.add(new GeneratedPlanItem(
                    planId,
                    place.id(),
                    itemType(place.category()),
                    place.name(),
                    index + 1,
                    start,
                    start.plusMinutes(duration)));
        }
        return result;
    }

    private PlanItemResponse toItem(PlanItemQueryResult item) {
        return new PlanItemResponse(
                item.id(),
                item.sequenceNo(),
                item.plannedStart(),
                item.plannedEnd(),
                item.status(),
                item.itemType(),
                item.title(),
                item.description(),
                item.estimatedCost(),
                item.memo(),
                item.placeId(),
                item.placeName(),
                item.category(),
                item.address(),
                item.latitude(),
                item.longitude());
    }

    private String itemType(String category) {
        return switch (category) {
            case "RESTAURANT", "CAFE" -> "MEAL";
            case "ACCOMMODATION" -> "ACCOMMODATION";
            default -> "PLACE";
        };
    }

    private record ProfileCode(
            char preparation,
            char place,
            char energy
    ) {
        private static ProfileCode from(String code) {
            String normalized = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
            if (!normalized.matches("[PS][NC][AR]")) {
                throw new BusinessException(ScheduleErrorCode.PLAN_PROFILE_INVALID);
            }
            return new ProfileCode(
                    normalized.charAt(0), normalized.charAt(1), normalized.charAt(2));
        }
    }
}
