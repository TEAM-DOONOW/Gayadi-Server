package com.gayadi.server.route;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.schedule.ScheduleErrorCode;
import com.gayadi.server.travel.TripErrorCode;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.Location;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.schedule.query.PlanPlaceQueryResult;
import com.gayadi.server.travel.model.DepartureMode;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.route.dto.response.RouteResponse;
import com.gayadi.server.route.query.RouteLockQueryResult;
import com.gayadi.server.route.query.RouteMemberQueryResult;
import com.gayadi.server.route.query.RouteOptionQueryResult;
import com.gayadi.server.route.query.RoutePlaceQueryResult;
import com.gayadi.server.route.query.RouteQueryResult;
import com.gayadi.server.route.query.RouteTripQueryResult;
import com.gayadi.server.route.query.RouteItineraryStop;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** 여행 일정과 참여자 조건을 바탕으로 경로 추천·선택 흐름을 조정합니다. */
@Service
public class RouteService {

    private static final int MAX_ITINERARY_STOPS = 100;
    private static final int MAX_OPTIMIZATION_STOPS = 12;

    private final RouteRepository repository;
    private final TripService trips;
    private final PlanService plans;
    private final List<RouteProvider> providers;
    private final JsonSupport json;
    private final TransactionTemplate transactions;

    public RouteService(RouteRepository repository, TripService trips, PlanService plans,
                        List<RouteProvider> providers, JsonSupport json,
                        PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.trips = trips;
        this.plans = plans;
        this.providers = List.copyOf(providers);
        this.json = json;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** 인증된 HTTP 요청에서 사용하는 경로 추천입니다. */
    public RouteResponse recommendForUser(
            long tripId, long userId, RoutePhase phase, Long requestedUserId) {
        return recommendForUser(tripId, userId, phase, requestedUserId, TransportMode.PUBLIC_TRANSIT);
    }

    public RouteResponse recommendForUser(
            long tripId, long userId, RoutePhase phase, Long requestedUserId,
            TransportMode transportMode) {
        RecommendationPreparation preparation = Objects.requireNonNull(
                transactions.execute(status -> {
                    RouteTripQueryResult trip = lockTrip(tripId);
                    trips.requireMember(tripId, userId);
                    long actorMemberId = participantIdForUser(tripId, userId);
                    Long memberId = resolveMemberId(
                            trip, phase, requestedUserId, userId, actorMemberId);
                    return prepare(tripId, trip, phase, memberId, userId);
                }));
        return routeResponse(recommendPrepared(preparation, transportMode));
    }

    /** 서비스 흐름 테스트와 내부 작업에서 사용하는 기존 진입점입니다. */
    public RouteResponse recommend(long tripId, RoutePhase phase, Long memberId) {
        RecommendationPreparation preparation = Objects.requireNonNull(
                transactions.execute(status -> prepare(
                        tripId, lockTrip(tripId), phase, memberId, null)));
        return routeResponse(recommendPrepared(preparation, TransportMode.PUBLIC_TRANSIT));
    }

    private RecommendationPreparation prepare(
            long tripId, RouteTripQueryResult trip, RoutePhase phase, Long memberId,
            Long authenticatedUserId) {
        RouteMemberQueryResult member = memberId == null ? null : member(tripId, memberId);
        RouteContext context = routeContext(tripId, trip, phase, member);
        return new RecommendationPreparation(
                tripId, phase, memberId, authenticatedUserId, context,
                routeRevision(tripId, memberId));
    }

    private Map<String, Object> recommendPrepared(
            RecommendationPreparation preparation, TransportMode transportMode) {
        RouteProvider provider = providers.stream()
                .filter(candidate -> candidate.transportMode() == transportMode)
                .findFirst()
                .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED));
        // 공급자 호출은 트랜잭션 밖에서 끝내 연결과 행 잠금을 오래 점유하지 않는다.
        RouteCalculation calculation = calculate(
                preparation.context(), preparation.phase(), provider);
        return Objects.requireNonNull(transactions.execute(status ->
                persistRecommendation(preparation, calculation)));
    }

    private RouteContext routeContext(
            long tripId,
            RouteTripQueryResult trip,
            RoutePhase phase,
            RouteMemberQueryResult member) {
        return switch (phase) {
            case DEPARTURE -> departureContext(trip, member);
            case RETURN -> returnContext(tripId, member);
            case IN_TRIP -> itineraryContext(tripId);
        };
    }

    private RouteCalculation calculate(RouteContext context, RoutePhase phase, RouteProvider provider) {
        List<RouteProvider.RouteEstimate> estimates;
        if (phase == RoutePhase.IN_TRIP) {
            SegmentCache cache = new SegmentCache(provider, phase);
            List<Location> ordered = new ArrayList<>(context.stops());
            for (OptimizationWindow window : context.windows()) {
                if (window.to() - window.from() + 1 > MAX_OPTIMIZATION_STOPS) {
                    throw new BusinessException(RouteErrorCode.ROUTE_OPTIMIZATION_TOO_LARGE);
                }
            }
            for (OptimizationWindow window : context.windows()) {
                List<Location> optimized = VisitOrderOptimizer.optimize(
                        ordered.subList(window.from(), window.to() + 1),
                        (from, to) -> cache.get(from, to).durationMinutes());
                for (int i = 0; i < optimized.size(); i++) {
                    ordered.set(window.from() + i, optimized.get(i));
                }
            }
            context = new RouteContext(ordered, context.scope(), context.windows());
            estimates = new ArrayList<>();
            for (int i = 1; i < ordered.size(); i++) {
                estimates.add(cache.get(ordered.get(i - 1), ordered.get(i)));
            }
        } else {
            estimates = provider.estimateSegments(context.stops(), phase.name());
        }
        if (estimates == null || estimates.size() != context.stops().size() - 1) {
            throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
        }
        List<SegmentEstimate> segments = new ArrayList<>();
        int durationMinutes = 0;
        int transferCount = 0;
        int fare = 0;
        String actualProvider = provider.providerName();
        for (int index = 0; index < context.stops().size() - 1; index++) {
            Location origin = context.stops().get(index);
            Location destination = context.stops().get(index + 1);
            RouteProvider.RouteEstimate estimate = estimates.get(index);
            if (estimate == null || estimate.durationMinutes() < 0
                    || estimate.transferCount() < 0 || estimate.fare() < 0) {
                throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
            }
            segments.add(new SegmentEstimate(index + 1, origin, destination, estimate));
            durationMinutes += estimate.durationMinutes();
            transferCount += estimate.transferCount();
            fare += estimate.fare();
            if (estimate.providerName() != null && !estimate.providerName().isBlank()
                    && !estimate.providerName().equals(provider.providerName())) {
                actualProvider = estimate.providerName();
            }
        }
        return new RouteCalculation(
                context,
                List.copyOf(segments),
                durationMinutes,
                transferCount,
                fare,
                actualProvider, provider.providerName(), provider.transportMode());
    }

    private Map<String, Object> persistRecommendation(
            RecommendationPreparation preparation,
            RouteCalculation calculation) {
        long tripId = preparation.tripId();
        RoutePhase phase = preparation.phase();
        Long memberId = preparation.memberId();
        Long authenticatedUserId = preparation.authenticatedUserId();
        lockTrip(tripId);
        if (!preparation.routeRevision().equals(routeRevision(tripId, memberId))) {
            throw new BusinessException(RouteErrorCode.ROUTE_CALCULATION_CHANGED);
        }
        if (authenticatedUserId != null) {
            trips.requireMember(tripId, authenticatedUserId);
            if (memberId != null) {
                long currentMemberId = participantIdForUser(tripId, authenticatedUserId);
                if (memberId != currentMemberId) {
                    throw new BusinessException(RouteErrorCode.ROUTE_CALCULATION_CHANGED);
                }
            }
        }
        long planId = getPlanId(tripId, phase);
        lockPlan(planId);
        expireActiveRoutes(planId, phase, memberId);

        List<Map<String, Object>> options = new ArrayList<>();
        for (OptionSpec option : optionSpecs(phase, calculation.transportMode())) {
            options.add(persistOption(
                    tripId,
                    planId,
                    phase,
                    memberId,
                    calculation,
                    option));
        }

        Map<String, Object> result = new LinkedHashMap<>(options.getFirst());
        result.put("options", List.copyOf(options));
        return result;
    }

    private Map<String, Object> persistOption(
            long tripId,
            long planId,
            RoutePhase phase,
            Long memberId,
            RouteCalculation calculation,
            OptionSpec option) {
        RouteContext context = calculation.context();
        List<Map<String, Object>> optionSegments = optionSegments(calculation, option);

        // 옵션별 보정이 반영된 구간을 합산해 저장용 요약 값을 계산합니다.
        int durationMinutes = optionSegments.stream()
                .mapToInt(segment -> ((Number) segment.get("durationMinutes")).intValue())
                .sum();
        int transferCount = optionSegments.stream()
                .mapToInt(segment -> ((Number) segment.get("transferCount")).intValue())
                .sum();
        int fare = optionSegments.stream()
                .mapToInt(segment -> ((Number) segment.get("fare")).intValue())
                .sum();
        Map<String, Object> routeData = new LinkedHashMap<>();
        routeData.put("provider", calculation.providerName());
        routeData.put("configuredProvider", calculation.configuredProvider());
        routeData.put("fallback", !calculation.providerName().equals(calculation.configuredProvider()));
        routeData.put("optionId", option.id());
        routeData.put("optionName", option.name());
        routeData.put("strategy", option.strategy());
        routeData.put("summary", option.summary());
        routeData.put("origin", context.origin());
        routeData.put("destination", context.destination());
        routeData.put("stops", context.stops());
        routeData.put("segments", optionSegments);

        // 계산 근거 전체를 JSON으로 보존해 조회 응답과 추후 재현에 사용합니다.
        long routeId = repository.saveRecommendation(
                planId,
                memberId,
                phase,
                calculation.transportMode(),
                json.write(routeData),
                durationMinutes,
                transferCount,
                fare);

        // 저장 모델과 무관한 API 계약 필드를 조립해 호출자에게 반환합니다.
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", routeId);
        result.put("optionId", option.id());
        result.put("name", option.name());
        result.put("tripId", tripId);
        result.put("planId", planId);
        putMemberContract(result, memberId);
        result.put("scope", context.scope());
        result.put("type", apiType(phase));
        result.put("phase", phase.name());
        result.put("origin", context.origin());
        result.put("destination", context.destination());
        result.put("stops", context.stops());
        result.put("segments", routeData.get("segments"));
        result.put("durationMinutes", durationMinutes);
        result.put("transferCount", transferCount);
        result.put("fare", fare);
        result.put("transportMode", calculation.transportMode().name());
        result.put("status", "RECOMMENDED");
        result.put("provider", calculation.providerName());
        result.put("configuredProvider", calculation.configuredProvider());
        result.put("fallback", !calculation.providerName().equals(calculation.configuredProvider()));
        result.put("summary", option.summary());
        return result;
    }

    private List<Map<String, Object>> optionSegments(
            RouteCalculation calculation,
            OptionSpec option) {
        return calculation.segments().stream()
                .map(segment -> {
                    int duration = Math.max(0, (int) Math.ceil(
                            segment.estimate().durationMinutes() * option.durationFactor()));
                    int transfers = option.fewerTransfers()
                            ? Math.max(0, segment.estimate().transferCount() - 1)
                            : segment.estimate().transferCount();
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("order", segment.order());
                    value.put("origin", segment.origin());
                    value.put("destination", segment.destination());
                    value.put("durationMinutes", duration);
                    value.put("transferCount", transfers);
                    value.put("fare", segment.estimate().fare());
                    String providerSummary = segment.estimate().summary();
                    value.put("summary", providerSummary == null || providerSummary.isBlank()
                            ? option.segmentSummary() : providerSummary);
                    value.put("strategySummary", option.segmentSummary());
                    return value;
                })
                .toList();
    }

    /** 추천 경로 번호를 사용해 참여자의 경로를 선택합니다. */
    @Transactional
    public RouteResponse selectForUser(
            long tripId, long userId, RoutePhase phase, long routeId) {
        return selectForUser(tripId, userId, phase, routeId, null, null);
    }

    /** 경로 번호 또는 추천 선택지로 참여자의 경로를 선택합니다. */
    @Transactional
    public RouteResponse selectForUser(
            long tripId, long userId, RoutePhase phase, Long requestedRouteId,
            String requestedOptionId, Long requestedUserId) {
        trips.requireMember(tripId, userId);
        trips.requireTrip(tripId);
        RouteTripQueryResult trip = lockTrip(tripId);
        long actorMemberId = participantIdForUser(tripId, userId);
        Long expectedMemberId = resolveMemberId(
                trip, phase, requestedUserId, userId, actorMemberId);

        long planId;
        long routeId;
        if (requestedRouteId != null) {
            routeId = requestedRouteId;
            planId = routePlanId(tripId, routeId);
            lockPlan(planId);
        } else {
            String optionId = normalizedOptionId(phase, requestedOptionId);
            planId = getPlanId(tripId, phase);
            lockPlan(planId);
            routeId = routeIdForOption(planId, phase, expectedMemberId, optionId);
        }
        RouteLockQueryResult route = lockedRoute(routeId, planId);

        RoutePhase routePhase = route.phase();
        if (routePhase != phase) {
            throw new BusinessException(RouteErrorCode.ROUTE_TYPE_MISMATCH);
        }
        Long routeMemberId = route.memberId();
        if (requestedRouteId != null && routeMemberId != null && routeMemberId == actorMemberId) {
            // 경로 번호는 이미 참여자 소유권을 식별하므로 TOGETHER 개인 출발안도 선택할 수 있다.
            expectedMemberId = actorMemberId;
        }
        if (!Objects.equals(routeMemberId, expectedMemberId)) {
            throw new BusinessException(RouteErrorCode.ROUTE_SELECTION_FORBIDDEN);
        }
        String status = route.status();
        if (!"RECOMMENDED".equals(status) && !"SELECTED".equals(status)) {
            throw new BusinessException(RouteErrorCode.ROUTE_NOT_SELECTABLE);
        }

        expireSelections(planId, phase, routeMemberId, routeId);
        repository.select(routeId);
        return routeResponse(routeView(routeById(tripId, routeId)));
    }

    private List<OptionSpec> optionSpecs(RoutePhase phase, TransportMode mode) {
        if (mode == TransportMode.CAR) {
            String id = switch (phase) {
                case DEPARTURE -> "fast";
                case IN_TRIP -> "balanced";
                case RETURN -> "home-fast";
            };
            return List.of(new OptionSpec(id, "자동차 이동", "FASTEST", 1.0, false,
                    "도로 예상 소요시간을 반영한 자동차 경로입니다.", "자동차 예상 이동 구간입니다."));
        }
        return switch (phase) {
            case DEPARTURE -> List.of(
                    new OptionSpec(
                            "fast",
                            "가장 빠른 출발",
                            "FASTEST",
                            1.0,
                            false,
                            "예상 이동 시간이 가장 짧은 출발안입니다.",
                            "이동 시간을 우선한 예상 구간입니다."),
                    new OptionSpec(
                            "easy",
                            "편안한 출발",
                            "FEWER_TRANSFERS",
                            1.15,
                            true,
                            "환승 부담과 대기 상황을 고려한 출발안입니다.",
                            "환승 부담을 줄이고 대기 여유를 둔 예상 구간입니다."));
            case IN_TRIP -> List.of(
                    new OptionSpec(
                            "balanced",
                            "균형 동선",
                            "BALANCED",
                            1.0,
                            false,
                            "일정의 고정 지점을 유지하고 이동 시간을 기준으로 계산한 동선입니다.",
                            "이동 시간과 환승을 함께 고려한 예상 구간입니다."),
                    new OptionSpec(
                            "crowd",
                            "한적한 동선",
                            "CROWD_BUFFER",
                            1.15,
                            false,
                            "혼잡 가능성에 대비해 이동 여유 시간을 둔 동선입니다.",
                            "혼잡 가능성에 대비한 여유 시간을 포함한 예상 구간입니다."));
            case RETURN -> List.of(
                    new OptionSpec(
                            "home-fast",
                            "빠른 귀가",
                            "FASTEST",
                            1.0,
                            false,
                            "마지막 일정 뒤 바로 이동하는 귀가안입니다.",
                            "이동 시간을 우선한 예상 구간입니다."),
                    new OptionSpec(
                            "home-rest",
                            "여유로운 귀가",
                            "REST_BUFFER",
                            1.2,
                            false,
                            "휴식과 대기 시간을 고려해 여유를 둔 귀가안입니다.",
                            "휴식과 대기 여유를 포함한 예상 구간입니다."));
        };
    }

    /** 참여자가 선택한 경로를 취소합니다. */
    @Transactional
    public void clearSelectionForUser(
            long tripId, long userId, RoutePhase phase, Long requestedUserId) {
        trips.requireMember(tripId, userId);
        trips.requireTrip(tripId);
        RouteTripQueryResult trip = lockTrip(tripId);
        long actorMemberId = participantIdForUser(tripId, userId);
        Long memberId = resolveMemberId(
                trip, phase, requestedUserId, userId, actorMemberId);
        long planId = getPlanId(tripId, phase);
        lockPlan(planId);

        repository.clearSelection(planId, phase, memberId);
    }

    /** 참여자가 선택한 여행 경로 목록을 조회합니다. */
    public List<RouteResponse> selectionsForUser(long tripId, long userId) {
        trips.requireMember(tripId, userId);
        long actorMemberId = participantIdForUser(tripId, userId);
        return repository.findSelections(tripId, actorMemberId).stream()
                .map(this::routeView)
                .map(this::routeResponse)
                .toList();
    }

    /** 교통 중단 등으로 더 이상 유효하지 않은 여행의 활성 경로를 모두 만료시킨다. */
    @Transactional
    public int expireActiveForTrip(long tripId) {
        trips.requireTrip(tripId);
        return repository.expireActiveForTrip(tripId);
    }

    /** API 경로 유형 문자열을 내부 경로 단계로 변환합니다. */
    public RoutePhase routePhase(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(RouteErrorCode.ROUTE_TYPE_REQUIRED);
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "DEPARTURE" -> RoutePhase.DEPARTURE;
            case "ITINERARY", "IN_TRIP" -> RoutePhase.IN_TRIP;
            case "HOME", "RETURN" -> RoutePhase.RETURN;
            default -> throw new BusinessException(RouteErrorCode.ROUTE_TYPE_INVALID);
        };
    }

    private RouteContext departureContext(
            RouteTripQueryResult trip,
            RouteMemberQueryResult member) {
        DepartureMode mode = trip.departureMode();
        long tripId = trip.id();
        Location firstPlace = placeLocation(plans.firstPlace(tripId));

        if (mode == DepartureMode.TOGETHER && member == null) {
            Long meetingPlaceId = trip.meetingPlaceId();
            if (meetingPlaceId == null) {
                throw new BusinessException(RouteErrorCode.ROUTE_MEETING_PLACE_REQUIRED);
            }
            return RouteContext.of(
                    placeLocation(getPlace(meetingPlaceId)), firstPlace, "GROUP");
        }
        if (member == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_MEMBER_REQUIRED);
        }
        Long departurePlaceId = member.departurePlaceId();
        if (departurePlaceId == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_DEPARTURE_PLACE_REQUIRED);
        }
        Location origin = placeLocation(getPlace(departurePlaceId));
        Location destination;
        if (mode == DepartureMode.TOGETHER) {
            Long meetingPlaceId = trip.meetingPlaceId();
            if (meetingPlaceId == null) {
                throw new BusinessException(RouteErrorCode.ROUTE_MEETING_PLACE_REQUIRED);
            }
            destination = placeLocation(getPlace(meetingPlaceId));
        } else {
            destination = firstPlace;
        }
        return RouteContext.of(origin, destination, "MEMBER");
    }

    private RouteContext returnContext(long tripId, RouteMemberQueryResult member) {
        if (member == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_MEMBER_REQUIRED);
        }
        Long returnPlaceId = member.returnPlaceId();
        if (returnPlaceId == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_RETURN_PLACE_REQUIRED);
        }
        return RouteContext.of(
                placeLocation(plans.lastPlace(tripId)),
                placeLocation(getPlace(returnPlaceId)),
                "MEMBER"
        );
    }

    private RouteContext itineraryContext(long tripId) {
        List<RouteItineraryStop> rows = repository.findItineraryStops(tripId, MAX_ITINERARY_STOPS + 1);
        if (rows.size() > MAX_ITINERARY_STOPS) {
            throw new BusinessException(RouteErrorCode.ROUTE_ITINERARY_TOO_LARGE);
        }
        if (rows.size() < 2) {
            throw new BusinessException(RouteErrorCode.ROUTE_STOPS_INSUFFICIENT);
        }
        List<OptimizationWindow> windows = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < rows.size(); i++) {
            RouteItineraryStop previous = rows.get(i - 1);
            RouteItineraryStop current = rows.get(i);
            if (current.planId() != previous.planId() || current.block() != previous.block()) {
                windows.add(new OptimizationWindow(start, i - 1));
                start = i;
            } else if (current.fixed()) {
                windows.add(new OptimizationWindow(start, i));
                start = i;
            }
        }
        windows.add(new OptimizationWindow(start, rows.size() - 1));
        return new RouteContext(rows.stream().map(RouteItineraryStop::location).toList(),
                "GROUP", List.copyOf(windows));
    }

    private String routeRevision(long tripId, Long memberId) {
        String schedule = repository.findScheduleRevision(tripId);
        String trip = repository.findTripRevision(tripId);
        if (trip == null) {
            throw new BusinessException(TripErrorCode.TRIP_NOT_FOUND);
        }
        String member = memberId == null
                ? "GROUP"
                : repository.findMemberRevision(memberId);
        return trip + "|" + member + "|" + schedule;
    }

    private String normalizedOptionId(RoutePhase phase, String requestedOptionId) {
        if (requestedOptionId == null || requestedOptionId.isBlank()) {
            throw new BusinessException(RouteErrorCode.ROUTE_OPTION_REQUIRED);
        }
        String optionId = requestedOptionId.trim().toLowerCase(Locale.ROOT);
        boolean allowed = switch (phase) {
            case DEPARTURE -> optionId.equals("fast") || optionId.equals("easy");
            case IN_TRIP -> optionId.equals("balanced") || optionId.equals("crowd");
            case RETURN -> optionId.equals("home-fast") || optionId.equals("home-rest");
        };
        if (!allowed) {
            throw new BusinessException(RouteErrorCode.ROUTE_OPTION_INVALID);
        }
        return optionId;
    }

    @SuppressWarnings("unchecked")
    private long routeIdForOption(
            long planId, RoutePhase phase, Long memberId, String optionId) {
        List<RouteOptionQueryResult> candidates = repository.findOptionCandidates(
                planId,
                phase,
                memberId);
        for (RouteOptionQueryResult candidate : candidates) {
            if (candidate.routeData() == null) {
                continue;
            }
            Map<String, Object> data = json.read(candidate.routeData(), Map.class);
            if (optionId.equals(data.get("optionId"))) {
                return candidate.id();
            }
        }
        throw new BusinessException(RouteErrorCode.ROUTE_SELECTABLE_NOT_FOUND);
    }

    private Long resolveMemberId(RouteTripQueryResult trip, RoutePhase phase,
                                 Long requestedUserId, long actorUserId, long actorMemberId) {
        if (requestedUserId != null && requestedUserId != actorUserId) {
            throw new BusinessException(RouteErrorCode.ROUTE_ACCESS_FORBIDDEN);
        }
        if (requestedUserId != null) {
            if (phase == RoutePhase.IN_TRIP) {
                throw new BusinessException(RouteErrorCode.ROUTE_GROUP_MEMBER_FORBIDDEN);
            }
            return actorMemberId;
        }
        if (phase == RoutePhase.RETURN) {
            return actorMemberId;
        }
        if (phase == RoutePhase.DEPARTURE
                && trip.departureMode() == DepartureMode.SEPARATE) {
            return actorMemberId;
        }
        return null;
    }

    private long getPlanId(long tripId, RoutePhase phase) {
        return java.util.Optional.ofNullable(repository.findPlanId(tripId, phase))
                .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_PLAN_REQUIRED));
    }

    private RouteTripQueryResult lockTrip(long tripId) {
        RouteTripQueryResult trip = repository.lockTrip(tripId);
        if (trip == null) {
            throw new BusinessException(TripErrorCode.TRIP_NOT_FOUND);
        }
        return trip;
    }

    private void lockPlan(long planId) {
        if (!repository.lockPlan(planId)) {
            throw new BusinessException(ScheduleErrorCode.SCHEDULE_NOT_FOUND);
        }
    }

    private long routePlanId(long tripId, long routeId) {
        return java.util.Optional.ofNullable(repository.findRoutePlanId(tripId, routeId))
                .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_RECOMMENDATION_NOT_FOUND));
    }

    private RouteLockQueryResult lockedRoute(long routeId, long planId) {
        RouteLockQueryResult route = repository.lockRoute(routeId, planId);
        if (route == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_RECOMMENDATION_NOT_FOUND);
        }
        return route;
    }

    private RouteQueryResult routeById(long tripId, long routeId) {
        RouteQueryResult route = repository.findRoute(tripId, routeId);
        if (route == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_RECOMMENDATION_NOT_FOUND);
        }
        return route;
    }

    private void expireActiveRoutes(long planId, RoutePhase phase, Long memberId) {
        repository.expireActive(planId, phase, memberId);
    }

    private void expireSelections(long planId, RoutePhase phase, Long memberId, long exceptRouteId) {
        repository.expireSelections(planId, phase, memberId, exceptRouteId);
    }

    private long participantIdForUser(long tripId, long userId) {
        return java.util.Optional.ofNullable(repository.findParticipantId(tripId, userId))
                .orElseThrow(() -> new BusinessException(TripErrorCode.TRIP_MEMBER_REQUIRED));
    }

    private RouteMemberQueryResult member(long tripId, long memberId) {
        RouteMemberQueryResult member = repository.findMember(tripId, memberId);
        if (member == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_MEMBER_NOT_FOUND);
        }
        return member;
    }

    private RoutePlaceQueryResult getPlace(long placeId) {
        RoutePlaceQueryResult place = repository.findPlace(placeId);
        if (place == null) {
            throw new BusinessException(RouteErrorCode.ROUTE_PLACE_NOT_FOUND);
        }
        return place;
    }

    private Location placeLocation(RoutePlaceQueryResult place) {
        return new Location(
                place.name(),
                place.latitude(),
                place.longitude()
        );
    }

    private Location placeLocation(PlanPlaceQueryResult place) {
        return new Location(place.name(), place.latitude(), place.longitude());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> routeView(RouteQueryResult row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.id());
        result.put("tripId", row.tripId());
        result.put("planId", row.planId());
        putMemberContract(
                result,
                row.memberId(),
                row.memberUserId());
        RoutePhase phase = row.phase();
        result.put("type", apiType(phase));
        result.put("phase", phase.name());
        result.put("transportMode", row.transportMode());
        putIfPresent(result, "durationMinutes", row.durationMinutes());
        putIfPresent(result, "distanceMeters", row.distanceMeters());
        putIfPresent(result, "transferCount", row.transferCount());
        putIfPresent(result, "fare", row.fare());
        result.put("status", row.status());
        putIfPresent(result, "recommendedAt", row.recommendedAt());
        putIfPresent(result, "selectedAt", row.selectedAt());
        String routeData = row.routeData();
        if (routeData != null) {
            Map<String, Object> data = json.read(routeData, Map.class);
            result.put("routeData", data);
            putRouteDataIfPresent(result, data, "provider", "provider");
            putRouteDataIfPresent(result, data, "configuredProvider", "configuredProvider");
            putRouteDataIfPresent(result, data, "fallback", "fallback");
            putRouteDataIfPresent(result, data, "optionId", "optionId");
            putRouteDataIfPresent(result, data, "name", "optionName");
            putRouteDataIfPresent(result, data, "summary", "summary");
            putRouteDataIfPresent(result, data, "stops", "stops");
            putRouteDataIfPresent(result, data, "segments", "segments");
        }
        return result;
    }

    private void putRouteDataIfPresent(
            Map<String, Object> target, Map<String, Object> routeData,
            String targetKey, String sourceKey) {
        Object value = routeData.get(sourceKey);
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private void putMemberContract(Map<String, Object> target, Long participantId) {
        putMemberContract(target, participantId, null);
    }

    private void putMemberContract(
            Map<String, Object> target, Long participantId, Long knownUserId) {
        if (participantId == null) {
            target.put("memberId", null);
            target.put("userId", null);
            target.put("participantId", null);
            return;
        }
        long userId = knownUserId == null
                ? userIdForParticipant(participantId)
                : knownUserId;
        target.put("memberId", userId);
        target.put("userId", userId);
        target.put("participantId", participantId);
    }

    private long userIdForParticipant(long participantId) {
        return java.util.Optional.ofNullable(repository.findUserId(participantId))
                .orElseThrow(() -> new BusinessException(RouteErrorCode.ROUTE_MEMBER_DATA_MISSING));
    }

    private String apiType(RoutePhase phase) {
        return switch (phase) {
            case DEPARTURE -> "DEPARTURE";
            case IN_TRIP -> "ITINERARY";
            case RETURN -> "HOME";
        };
    }

    private RouteResponse routeResponse(Map<String, Object> value) {
        return json.convert(value, RouteResponse.class);
    }

    private void putIfPresent(Map<String, Object> target, String targetKey, Object value) {
        if (value != null) {
            target.put(targetKey, value);
        }
    }

    private record RouteContext(
            List<Location> stops,
            String scope,
            List<OptimizationWindow> windows
    ) {
        private RouteContext {
            stops = List.copyOf(stops);
            windows = List.copyOf(windows);
            if (stops.size() < 2) {
                throw new IllegalArgumentException("경로에는 장소가 두 곳 이상 필요합니다.");
            }
        }

        private static RouteContext of(Location origin, Location destination, String scope) {

            return new RouteContext(List.of(origin, destination), scope, List.of());

        }
        private Location origin() {
            return stops.getFirst();
        }
        private Location destination() {
            return stops.getLast();
        }
    }

    private record SegmentEstimate(
            int order,
            Location origin,
            Location destination,
            RouteProvider.RouteEstimate estimate
    ) {
    }

    private record RecommendationPreparation(
            long tripId,
            RoutePhase phase,
            Long memberId,
            Long authenticatedUserId,
            RouteContext context,
            String routeRevision
    ) {
    }

    private record OptionSpec(
            String id,
            String name,
            String strategy,
            double durationFactor,
            boolean fewerTransfers,
            String summary,
            String segmentSummary
    ) {
    }

    private record RouteCalculation(
            RouteContext context,
            List<SegmentEstimate> segments,
            int durationMinutes,
            int transferCount,
            int fare,
            String providerName,
            String configuredProvider,
            TransportMode transportMode
    ) {
    }
    private record OptimizationWindow(int from, int to) {
    }

    private record SegmentKey(Location origin, Location destination) {
    }

    /** 요청 단위 방향별 캐시. 실패한 계산은 저장하지 않습니다. */
    private static final class SegmentCache {
        private final RouteProvider provider;
        private final RoutePhase phase;
        private final Map<SegmentKey, RouteProvider.RouteEstimate> estimates = new HashMap<>();
        private final long started = System.nanoTime();

        private SegmentCache(RouteProvider provider, RoutePhase phase) {
            this.provider = provider;
            this.phase = phase;
        }

        private RouteProvider.RouteEstimate get(Location from, Location to) {
            SegmentKey key = new SegmentKey(from, to);
            RouteProvider.RouteEstimate cached = estimates.get(key);
            if (cached != null) {
                return cached;
            }
            if (estimates.size() >= 500 || System.nanoTime() - started > Duration.ofSeconds(30).toNanos()) {
                throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
            }
            List<RouteProvider.RouteEstimate> result = provider.estimateSegments(List.of(from, to), phase.name());
            if (result == null || result.size() != 1 || result.getFirst() == null
                    || result.getFirst().durationMinutes() < 0 || result.getFirst().fare() < 0
                    || result.getFirst().transferCount() < 0) {
                throw new BusinessException(RouteErrorCode.ROUTE_PROVIDER_FAILED);
            }
            estimates.put(key, result.getFirst());
            return result.getFirst();
        }
    }

}
