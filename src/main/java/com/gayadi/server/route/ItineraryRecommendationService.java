package com.gayadi.server.route;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.place.PlaceRepository;
import com.gayadi.server.place.model.PlaceCategory;
import com.gayadi.server.place.query.PlaceQueryResult;
import com.gayadi.server.route.dto.request.ItineraryRecommendationRequest;
import com.gayadi.server.route.dto.request.ItinerarySelectionRequest;
import com.gayadi.server.route.dto.response.ItineraryRecommendationResponse;
import com.gayadi.server.route.dto.response.ItineraryStopResponse;
import com.gayadi.server.schedule.ScheduleItemService;
import com.gayadi.server.schedule.ScheduleItemService.RouteStopCommand;
import com.gayadi.server.schedule.ScheduleErrorCode;
import com.gayadi.server.tourapi.TourDiscoveryService;
import com.gayadi.server.tourapi.dto.request.TourDiscoveryRequest;
import com.gayadi.server.travel.TripErrorCode;
import com.gayadi.server.travel.TripRepository;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.query.TripQueryResult;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 자유여행과 달리 장소·순서·체류시간을 하나의 루트로 추천합니다. */
@Service
public class ItineraryRecommendationService {

    private static final int MAX_CANDIDATES = 40;
    private static final int MAX_STOPS = 6;
    /** variation마다 첫 장소를 고를 후보 수입니다. 그중 주변 후보가 가장 많은 곳에서 시작합니다. */
    private static final int FIRST_STOP_WINDOW = 3;
    private static final int MIN_DENSE_CANDIDATES = 8;
    private static final double CLUSTER_RADIUS_KM = 2.5;
    private static final double OFF_MEAL_RESTAURANT = 6.0;
    private static final int LUNCH_START = 11 * 60 + 30;
    private static final int LUNCH_END = 13 * 60 + 30;
    private static final int DINNER_START = 17 * 60 + 30;
    private static final int DINNER_END = 19 * 60 + 30;

    private final TripService trips;
    private final TripRepository tripRepository;
    private final PlaceRepository places;
    private final ScheduleItemService schedules;
    private final TourDiscoveryService discovery;

    public ItineraryRecommendationService(
            TripService trips,
            TripRepository tripRepository,
            PlaceRepository places,
            ScheduleItemService schedules,
            TourDiscoveryService discovery) {
        this.trips = trips;
        this.tripRepository = tripRepository;
        this.places = places;
        this.schedules = schedules;
        this.discovery = discovery;
    }

    public ItineraryRecommendationResponse recommend(
            long userId, long tripId, ItineraryRecommendationRequest request) {
        trips.requireMember(tripId, userId);
        TripQueryResult trip = tripRepository.find(tripId)
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_NOT_FOUND));
        LocalDate date = AppDateFormat.parseDate(request.date(), "여행루트 날짜");
        if (date.isBefore(trip.startDate()) || date.isAfter(trip.endDate())) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_DATE_OUTSIDE_TRIP);
        }
        LocalTime start = AppDateFormat.parseTime(request.startTime(), "여행루트 시작 시각");
        LocalTime end = AppDateFormat.parseTime(request.endTime(), "여행루트 종료 시각");
        long availableMinutes = Duration.between(start, end).toMinutes();
        if (availableMinutes < 180 || availableMinutes > 720) {
            throw new BusinessException(RouteErrorCode.ROUTE_DAY_RANGE_INVALID);
        }

        String city = tripRepository.findCities(tripId).stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_REGION_REQUIRED));
        List<PlaceQueryResult> candidates = routeCandidates(city);
        if (candidates.size() < MIN_DENSE_CANDIDATES && discovery != null) {
            // 저장 장소가 적으면 동선이 흩어지므로 관광정보로 지역 장소를 보충한 뒤 다시 고릅니다.
            try {
                discovery.discover(new TourDiscoveryRequest(20, city, null, null, null, null, null));
                candidates = routeCandidates(city);
            } catch (BusinessException exception) {
                // 로컬 후보만으로 루트를 만들 수 있으면 외부 관광정보 보강은 best-effort로 처리합니다.
                if (candidates.size() < 2) {
                    throw exception;
                }
            }
        }
        if (candidates.size() < 2) {
            throw new BusinessException(RouteErrorCode.ROUTE_CANDIDATES_INSUFFICIENT);
        }
        return build(request, candidates, start, end);
    }

    /** 미리 본 루트와 같은 결과일 때만 해당 날짜의 MAIN 일정을 교체합니다. */
    public ItineraryRecommendationResponse apply(
            long userId, long tripId, String rawDate, ItinerarySelectionRequest selection) {
        LocalDate date = AppDateFormat.parseDate(rawDate, "여행루트 날짜");
        ItineraryRecommendationRequest request = new ItineraryRecommendationRequest(
                AppDateFormat.date(date), selection.startTime(), selection.endTime(),
                selection.transportMode(), selection.variation(), selection.expectedPlaceIds());
        ItineraryRecommendationResponse recommendation = recommend(userId, tripId, request);
        List<Long> actualPlaceIds = recommendation.stops().stream()
                .map(ItineraryStopResponse::placeId)
                .toList();
        if (!selection.expectedPlaceIds().equals(actualPlaceIds)) {
            throw new BusinessException(RouteErrorCode.ROUTE_CALCULATION_CHANGED);
        }
        schedules.replaceMainRoute(
                userId,
                tripId,
                date,
                recommendation.stops().stream()
                        .map(stop -> new RouteStopCommand(
                                stop.placeId(),
                                stop.name(),
                                AppDateFormat.parseTime(stop.arrivalTime(), "방문 시작 시각"),
                                AppDateFormat.parseTime(stop.departureTime(), "방문 종료 시각"),
                                "여행루트 추천 · 체류 " + stop.stayMinutes() + "분"))
                        .toList());
        return recommendation;
    }

    ItineraryRecommendationResponse build(
            ItineraryRecommendationRequest request,
            List<PlaceQueryResult> candidates,
            LocalTime start,
            LocalTime end) {
        List<PlaceQueryResult> pool = rotated(candidates, request.variation());
        List<ItineraryStopResponse> stops = new ArrayList<>();
        // 시작 시각 기준 분 단위로 계산해 LocalTime이 자정을 넘어 되감기지 않게 합니다.
        long limit = Duration.between(start, end).toMinutes();
        long cursor = 0;
        PlaceQueryResult previous = null;
        Set<Long> used = new HashSet<>();
        Set<Meal> served = new HashSet<>();
        int startClock = start.getHour() * 60 + start.getMinute();
        int totalTravel = 0;
        int totalStay = 0;
        while (stops.size() < MAX_STOPS) {
            DayState day = new DayState(startClock, cursor, limit, served);
            PlaceQueryResult place = previous == null
                    ? firstStop(pool, used, request.transportMode(), day, request.variation())
                    : nearestStop(pool, used, previous, request.transportMode(), day);
            if (place == null) {
                break;
            }
            Leg leg = previous == null
                    ? new Leg(0, 0)
                    : leg(previous, place, request.transportMode());
            int stay = stayMinutes(place.category());
            long arrival = cursor + leg.minutes();
            long departure = arrival + stay;
            if (departure > limit) {
                break;
            }
            stops.add(new ItineraryStopResponse(
                    stops.size() + 1,
                    place.id(),
                    place.name(),
                    categoryLabel(place.category()),
                    place.imageUrl(),
                    place.latitude(),
                    place.longitude(),
                    AppDateFormat.time(start.plusMinutes(arrival)),
                    AppDateFormat.time(start.plusMinutes(departure)),
                    stay,
                    leg.minutes(),
                    leg.distanceMeters()));
            totalTravel += leg.minutes();
            totalStay += stay;
            if (place.category() == PlaceCategory.RESTAURANT) {
                Meal meal = mealAt(startClock + (int) arrival);
                if (meal != null) {
                    served.add(meal);
                }
            }
            cursor = departure;
            previous = place;
            used.add(place.id());
        }
        if (stops.size() < 2) {
            throw new BusinessException(RouteErrorCode.ROUTE_CANDIDATES_INSUFFICIENT);
        }
        String summary = stops.size() + "곳을 " + totalStay + "분 동안 둘러보고, "
                + "이동에는 약 " + totalTravel + "분이 걸려요.";
        return new ItineraryRecommendationResponse(
                AppDateFormat.date(AppDateFormat.parseDate(request.date(), "여행루트 날짜")), request.startTime(), request.endTime(), request.transportMode(),
                request.variation(), true, totalTravel, totalStay, summary, List.copyOf(stops));
    }

    private List<PlaceQueryResult> routeCandidates(String city) {
        return places.findItineraryCandidates(city, MAX_CANDIDATES).stream()
                .filter(this::routeCandidate)
                .toList();
    }

    private List<PlaceQueryResult> rotated(List<PlaceQueryResult> values, int variation) {
        List<PlaceQueryResult> rotated = new ArrayList<>(values);
        // 후보가 3개일 때도 variation=1이 제자리 회전이 되지 않도록 한 칸씩 이동합니다.
        int shift = Math.floorMod(variation, rotated.size());
        Collections.rotate(rotated, -shift);
        return rotated;
    }

    /**
     * 기본 루트는 회전한 후보 앞쪽에서 반경 안 후보가 가장 많은 곳을 첫 장소로 골라 동선이 한 지역에 모이게 합니다.
     * 식사 시간이 아니면 식당으로 시작하지 않습니다.
     */
    private PlaceQueryResult firstStop(
            List<PlaceQueryResult> pool,
            Set<Long> used,
            TransportMode mode,
            DayState day,
            int variation) {
        List<PlaceQueryResult> fitting = pool.stream()
                .filter(candidate -> !used.contains(candidate.id()) && fits(null, candidate, mode, day))
                .toList();
        List<PlaceQueryResult> preferred = fitting.stream()
                .filter(candidate -> mealPenalty(candidate, day.clock()) <= 0)
                .limit(FIRST_STOP_WINDOW)
                .toList();
        List<PlaceQueryResult> window = preferred.isEmpty()
                ? fitting.stream().limit(FIRST_STOP_WINDOW).toList()
                : preferred;
        if (variation > 0 && !window.isEmpty()) {
            // 다른 루트 요청은 회전 순서의 첫 장소에서 시작해 기본 루트와 겹치지 않게 합니다.
            return window.getFirst();
        }
        PlaceQueryResult selected = null;
        long bestNeighbors = -1;
        for (PlaceQueryResult candidate : window) {
            long neighbors = pool.stream()
                    .filter(other -> other.id() != candidate.id()
                            && distanceKm(candidate, other) <= CLUSTER_RADIUS_KM)
                    .count();
            if (neighbors > bestNeighbors) {
                bestNeighbors = neighbors;
                selected = candidate;
            }
        }
        return selected;
    }

    /** 직전 장소에서 남은 시간 안에 도착할 수 있는 장소 중 거리·유형·식사 시간을 함께 따져 고릅니다. */
    private PlaceQueryResult nearestStop(
            List<PlaceQueryResult> pool,
            Set<Long> used,
            PlaceQueryResult origin,
            TransportMode mode,
            DayState day) {
        PlaceQueryResult selected = null;
        double best = Double.POSITIVE_INFINITY;
        for (PlaceQueryResult candidate : pool) {
            if (used.contains(candidate.id()) || !fits(origin, candidate, mode, day)) {
                continue;
            }
            double score = distanceKm(origin, candidate);
            if (candidate.category() == origin.category()) {
                score = score * 1.35 + 0.5;
            }
            int arrivalClock = day.clock() + leg(origin, candidate, mode).minutes();
            double mealPenalty = mealPenalty(candidate, arrivalClock, day.served());
            if (mealPenalty >= OFF_MEAL_RESTAURANT) {
                // 식사 시간 밖의 식당으로 빈 시간을 채우기보다 루트를 일찍 마칩니다.
                continue;
            }
            score += mealPenalty;
            if (score < best) {
                best = score;
                selected = candidate;
            }
        }
        return selected;
    }

    /** 식당은 아직 먹지 않은 식사 시간에 두고, 식사 시간 밖의 식당은 멀리 있는 장소만큼 불리하게 봅니다. */
    private double mealPenalty(PlaceQueryResult candidate, int clock, Set<Meal> served) {
        Meal meal = mealAt(clock);
        boolean restaurant = candidate.category() == PlaceCategory.RESTAURANT;
        if (restaurant) {
            return meal != null && !served.contains(meal) ? -1.5 : OFF_MEAL_RESTAURANT;
        }
        return meal != null && !served.contains(meal) ? 1.0 : 0.0;
    }

    private double mealPenalty(PlaceQueryResult candidate, int clock) {
        return mealPenalty(candidate, clock, Set.of());
    }

    private static Meal mealAt(int clock) {
        if (clock >= LUNCH_START && clock <= LUNCH_END) {
            return Meal.LUNCH;
        }
        if (clock >= DINNER_START && clock <= DINNER_END) {
            return Meal.DINNER;
        }
        return null;
    }

    private boolean fits(
            PlaceQueryResult from,
            PlaceQueryResult to,
            TransportMode mode,
            DayState day) {
        int travel = from == null ? 0 : leg(from, to, mode).minutes();
        return day.cursor() + travel + stayMinutes(to.category()) <= day.limit();
    }

    private boolean routeCandidate(PlaceQueryResult place) {
        return place.latitude() != null && place.longitude() != null
                && place.category() != PlaceCategory.ACCOMMODATION
                && place.category() != PlaceCategory.SHELTER;
    }

    private int stayMinutes(PlaceCategory category) {
        return switch (category) {
            case RESTAURANT -> 60;
            case CAFE -> 45;
            case SHOPPING -> 75;
            case CULTURE -> 90;
            case ATTRACTION, ETC -> 80;
            case ACCOMMODATION, SHELTER -> 60;
        };
    }

    private String categoryLabel(PlaceCategory category) {
        return switch (category) {
            case ATTRACTION -> "관광명소";
            case RESTAURANT -> "맛집";
            case CAFE -> "카페";
            case CULTURE -> "문화";
            case SHOPPING -> "쇼핑";
            case ACCOMMODATION -> "숙소";
            case SHELTER -> "실내";
            case ETC -> "장소";
        };
    }

    private Leg leg(PlaceQueryResult from, PlaceQueryResult to, TransportMode mode) {
        double distanceKm = distanceKm(from, to);
        double minutes = switch (mode) {
            case CAR -> distanceKm / 28.0 * 60.0 + 5.0;
            case PUBLIC_TRANSIT -> distanceKm / 20.0 * 60.0 + 8.0;
            case WALK -> distanceKm / 4.2 * 60.0;
            case BICYCLE -> distanceKm / 14.0 * 60.0;
        };
        return new Leg(Math.max(5, (int) Math.ceil(minutes)), (int) Math.round(distanceKm * 1000.0));
    }

    private double distanceKm(PlaceQueryResult from, PlaceQueryResult to) {
        double earthRadius = 6371.0;
        double latDistance = Math.toRadians(to.latitude() - from.latitude());
        double lonDistance = Math.toRadians(to.longitude() - from.longitude());
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(from.latitude())) * Math.cos(Math.toRadians(to.latitude()))
                * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record Leg(int minutes, int distanceMeters) {
    }

    private enum Meal { LUNCH, DINNER }

    /** 시작 시각 기준 경과 분(cursor)과 하루 한도, 이미 채운 식사를 묶습니다. */
    private record DayState(int startClock, long cursor, long limit, Set<Meal> served) {
        int clock() {
            return startClock + (int) cursor;
        }
    }
}
