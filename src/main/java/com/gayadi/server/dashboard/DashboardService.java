package com.gayadi.server.dashboard;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.dashboard.dto.response.DashboardProgressResponse;
import com.gayadi.server.dashboard.dto.response.DashboardResponse;
import com.gayadi.server.dashboard.dto.response.TripDayResponse;
import com.gayadi.server.event.EventService;
import com.gayadi.server.event.dto.response.ChangeProposalResponse;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.schedule.dto.response.ScheduleResponse;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.dto.response.ParticipantResponse;
import com.gayadi.server.travel.dto.response.TripResponse;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** 여행 홈에 필요한 여러 도메인의 조회 결과를 조합합니다. */
@Service
public class DashboardService {

    private static final int MAX_VISIBLE_PROPOSALS = 20;

    private final TripService trips;
    private final ScheduleItemService schedules;
    private final EventService events;

    public DashboardService(
            TripService trips,
            ScheduleItemService schedules,
            EventService events) {
        this.trips = trips;
        this.schedules = schedules;
        this.events = events;
    }

    /** 일정과 진행 상태를 조합해 여행 대시보드를 구성합니다. */
    public DashboardResponse dashboard(long userId, long tripId) {
        trips.requireMember(tripId, userId);
        TripResponse trip = trips.view(tripId);
        List<ParticipantResponse> participants = trips.members(tripId);
        List<ScheduleResponse> scheduleItems = schedules.list(userId, tripId);
        List<ChangeProposalResponse> pendingProposals = events.pendingProposals(
                tripId, MAX_VISIBLE_PROPOSALS);

        LocalDate today = LocalDate.now();
        LocalDate startDate = localDate(trip.startDate());
        LocalDate endDate = localDate(trip.endDate());
        long visitedCount = scheduleItems.stream()
                .filter(ScheduleResponse::isVisited)
                .count();
        int progress = scheduleItems.isEmpty()
                ? 0
                : Math.toIntExact(visitedCount * 100 / scheduleItems.size());

        return new DashboardResponse(
                trip,
                tripDays(startDate, endDate),
                ChronoUnit.DAYS.between(today, startDate),
                participants,
                participants.size(),
                scheduleItems,
                scheduleItems.stream()
                        .filter(item -> today.equals(localDate(item.date())))
                        .toList(),
                new DashboardProgressResponse(scheduleItems.size(), visitedCount, progress),
                pendingProposals,
                LocalDateTime.now());
    }

    private List<TripDayResponse> tripDays(LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        return java.util.stream.LongStream.rangeClosed(0, days)
                .mapToObj(offset -> startDate.plusDays(offset))
                .map(date -> new TripDayResponse(
                        Math.toIntExact(ChronoUnit.DAYS.between(startDate, date)) + 1,
                        AppDateFormat.date(date),
                        date.getMonthValue() + "." + date.getDayOfMonth()
                                + "/" + weekday(date.getDayOfWeek())))
                .toList();
    }

    private String weekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "월";
            case TUESDAY -> "화";
            case WEDNESDAY -> "수";
            case THURSDAY -> "목";
            case FRIDAY -> "금";
            case SATURDAY -> "토";
            case SUNDAY -> "일";
        };
    }

    private LocalDate localDate(Object value) {
        return AppDateFormat.databaseDate(value);
    }
}
