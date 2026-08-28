package com.gayadi.server.dashboard;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.event.EventService;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.travel.TripService;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    public Map<String, Object> dashboard(long userId, long tripId) {
        trips.requireMember(tripId, userId);
        Map<String, Object> trip = trips.view(tripId);
        List<Map<String, Object>> participants = trips.members(tripId);
        List<Map<String, Object>> scheduleItems = schedules.list(userId, tripId);
        List<Map<String, Object>> pendingProposals = events.pendingProposals(
                        tripId, MAX_VISIBLE_PROPOSALS).stream()
                .map(this::proposalSummary)
                .toList();

        LocalDate today = LocalDate.now();
        LocalDate startDate = localDate(trip.get("startDate"));
        LocalDate endDate = localDate(trip.get("endDate"));
        long visitedCount = scheduleItems.stream()
                .filter(item -> Boolean.TRUE.equals(item.get("isVisited")))
                .count();
        int progress = scheduleItems.isEmpty()
                ? 0
                : Math.toIntExact(visitedCount * 100 / scheduleItems.size());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trip", trip);
        result.put("tripDays", tripDays(startDate, endDate));
        result.put("daysUntilStart", ChronoUnit.DAYS.between(today, startDate));
        result.put("participants", participants);
        result.put("participantCount", participants.size());
        result.put("schedules", scheduleItems);
        result.put("todaySchedules", scheduleItems.stream()
                .filter(item -> today.equals(localDate(item.get("date"))))
                .toList());
        result.put("progress", Map.of(
                "scheduleCount", scheduleItems.size(),
                "visitedCount", visitedCount,
                "percentage", progress));
        result.put("pendingChangeProposals", pendingProposals);
        result.put("generatedAt", LocalDateTime.now());
        return result;
    }

    private List<Map<String, Object>> tripDays(LocalDate startDate, LocalDate endDate) {
        long days = ChronoUnit.DAYS.between(startDate, endDate);
        return java.util.stream.LongStream.rangeClosed(0, days)
                .mapToObj(offset -> startDate.plusDays(offset))
                .map(date -> {
                    Map<String, Object> day = new LinkedHashMap<>();
                    day.put("dayNumber", Math.toIntExact(ChronoUnit.DAYS.between(startDate, date)) + 1);
                    day.put("date", AppDateFormat.date(date));
                    day.put("dateLabel", date.getMonthValue() + "." + date.getDayOfMonth()
                            + "/" + weekday(date.getDayOfWeek()));
                    return day;
                })
                .toList();
    }

    private Map<String, Object> proposalSummary(Map<String, Object> row) {
        Map<String, Object> proposal = new LinkedHashMap<>();
        proposal.put("id", RowSupport.longValue(row, "id"));
        proposal.put("type", nullable(row, "type"));
        proposal.put("reason", nullable(row, "reason"));
        proposal.put("status", RowSupport.strValue(row, "status"));
        proposal.put("baseRevisionNo", nullable(row, "baseRevisionNo"));
        proposal.put("options", row.getOrDefault("options", List.of()));
        proposal.put("generatedAt", nullable(row, "generatedAt"));
        return proposal;
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

    private Object nullable(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        return row.get(key.toUpperCase(Locale.ROOT));
    }
}
