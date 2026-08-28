package com.gayadi.server.route;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.Location;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.travel.DepartureMode;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class RouteService {

    private static final int MAX_ITINERARY_STOPS = 100;

    private final JdbcClient jdbc;
    private final TripService trips;
    private final PlanService plans;
    private final RouteProvider provider;
    private final JsonSupport json;
    private final KeyHelper keyHelper;
    private final TransactionTemplate transactions;

    public RouteService(JdbcClient jdbc, TripService trips, PlanService plans,
                        RouteProvider provider, JsonSupport json, KeyHelper keyHelper,
                        PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.plans = plans;
        this.provider = provider;
        this.json = json;
        this.keyHelper = keyHelper;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** 인증된 HTTP 요청에서 사용하는 경로 추천입니다. */
    public Map<String, Object> recommendForUser(
            long tripId, long userId, RoutePhase phase, Long requestedUserId) {
        RecommendationPreparation preparation = Objects.requireNonNull(
                transactions.execute(status -> {
                    Map<String, Object> trip = lockTrip(tripId);
                    trips.requireMember(tripId, userId);
                    long actorMemberId = participantIdForUser(tripId, userId);
                    Long memberId = resolveMemberId(
                            trip, phase, requestedUserId, userId, actorMemberId);
                    return prepare(tripId, trip, phase, memberId, userId);
                }));
        return recommendPrepared(preparation);
    }

    /** 서비스 흐름 테스트와 내부 작업에서 사용하는 기존 진입점입니다. */
    public Map<String, Object> recommend(long tripId, RoutePhase phase, Long memberId) {
        RecommendationPreparation preparation = Objects.requireNonNull(
                transactions.execute(status -> prepare(
                        tripId, lockTrip(tripId), phase, memberId, null)));
        return recommendPrepared(preparation);
    }

    private RecommendationPreparation prepare(
            long tripId, Map<String, Object> trip, RoutePhase phase, Long memberId,
            Long authenticatedUserId) {
        Map<String, Object> member = memberId == null ? null : member(tripId, memberId);
        RouteContext context = routeContext(tripId, trip, phase, member);
        return new RecommendationPreparation(
                tripId, phase, memberId, authenticatedUserId, context,
                routeRevision(tripId, memberId));
    }

    private Map<String, Object> recommendPrepared(RecommendationPreparation preparation) {
        // 공급자 호출은 트랜잭션 밖에서 끝내 연결과 행 잠금을 오래 점유하지 않는다.
        RouteCalculation calculation = calculate(
                preparation.context(), preparation.phase());
        return Objects.requireNonNull(transactions.execute(status ->
                persistRecommendation(preparation, calculation)));
    }

    private RouteContext routeContext(
            long tripId, Map<String, Object> trip, RoutePhase phase, Map<String, Object> member) {
        return switch (phase) {
            case DEPARTURE -> departureContext(trip, member);
            case RETURN -> returnContext(tripId, member);
            case IN_TRIP -> itineraryContext(tripId);
        };
    }

    private RouteCalculation calculate(RouteContext context, RoutePhase phase) {
        List<RouteProvider.RouteEstimate> estimates = provider.estimateSegments(
                context.stops(), phase.name());
        if (estimates == null || estimates.size() != context.stops().size() - 1) {
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "경로 공급자가 전체 이동 구간을 계산하지 못했습니다.");
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
                throw new ApiException(HttpStatus.BAD_GATEWAY,
                        "경로 공급자가 올바르지 않은 이동 정보를 보냈습니다.");
            }
            segments.add(new SegmentEstimate(index + 1, origin, destination, estimate));
            durationMinutes += estimate.durationMinutes();
            transferCount += estimate.transferCount();
            fare += estimate.fare();
            if (estimate.providerName() != null && !estimate.providerName().isBlank()) {
                actualProvider = estimate.providerName();
            }
        }
        return new RouteCalculation(context, List.copyOf(segments),
                durationMinutes, transferCount, fare, actualProvider);
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
            throw new ApiException(HttpStatus.CONFLICT,
                    "경로를 계산하는 동안 일정이 바뀌었습니다. 다시 요청해 주세요.");
        }
        if (authenticatedUserId != null) {
            trips.requireMember(tripId, authenticatedUserId);
            if (memberId != null) {
                long currentMemberId = participantIdForUser(tripId, authenticatedUserId);
                if (memberId != currentMemberId) {
                    throw new ApiException(HttpStatus.CONFLICT,
                            "경로를 계산하는 동안 참여자 정보가 바뀌었습니다. 다시 요청해 주세요.");
                }
            }
        }
        long planId = getPlanId(tripId, phase);
        lockPlan(planId);
        expireActiveRoutes(planId, phase, memberId);

        List<Map<String, Object>> options = new ArrayList<>();
        for (OptionSpec option : optionSpecs(phase)) {
            options.add(persistOption(
                    tripId, planId, phase, memberId, calculation, option));
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
        routeData.put("configuredProvider", provider.providerName());
        routeData.put("fallback", !calculation.providerName().equals(provider.providerName()));
        routeData.put("optionId", option.id());
        routeData.put("optionName", option.name());
        routeData.put("strategy", option.strategy());
        routeData.put("summary", option.summary());
        routeData.put("origin", context.origin());
        routeData.put("destination", context.destination());
        routeData.put("stops", context.stops());
        routeData.put("segments", optionSegments);

        long routeId = keyHelper.insert("""
                INSERT INTO travel_routes (plan_id, member_id, phase, route_data, transport_mode,
                                            duration_minutes, transfer_count, fare, status, recommended_at)
                VALUES (?, ?, ?, ?, 'PUBLIC_TRANSIT', ?, ?, ?, 'RECOMMENDED', CURRENT_TIMESTAMP)
                """,
                planId, memberId, phase.name(), json.write(routeData),
                durationMinutes, transferCount, fare);

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
        result.put("transportMode", "PUBLIC_TRANSIT");
        result.put("status", "RECOMMENDED");
        result.put("provider", calculation.providerName());
        result.put("configuredProvider", provider.providerName());
        result.put("fallback", !calculation.providerName().equals(provider.providerName()));
        result.put("summary", option.summary());
        return result;
    }

    private List<Map<String, Object>> optionSegments(
            RouteCalculation calculation, OptionSpec option) {
        return calculation.segments().stream()
                .map(segment -> {
                    int duration = Math.max(1, (int) Math.ceil(
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

    @Transactional
    public Map<String, Object> selectForUser(
            long tripId, long userId, RoutePhase phase, long routeId) {
        return selectForUser(tripId, userId, phase, routeId, null, null);
    }

    @Transactional
    public Map<String, Object> selectForUser(
            long tripId, long userId, RoutePhase phase, Long requestedRouteId,
            String requestedOptionId, Long requestedUserId) {
        trips.requireMember(tripId, userId);
        Map<String, Object> trip = trips.requireTrip(tripId);
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
        Map<String, Object> route = lockedRoute(routeId, planId);

        RoutePhase routePhase = RoutePhase.valueOf(RowSupport.strValue(route, "phase"));
        if (routePhase != phase) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "경로 종류와 추천 경로가 서로 다릅니다.");
        }
        Long routeMemberId = nullableLong(route, "member_id");
        if (requestedRouteId != null && routeMemberId != null && routeMemberId == actorMemberId) {
            // 경로 번호는 이미 참여자 소유권을 식별하므로 TOGETHER 개인 출발안도 선택할 수 있다.
            expectedMemberId = actorMemberId;
        }
        if (!Objects.equals(routeMemberId, expectedMemberId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "자신의 경로만 선택할 수 있습니다.");
        }
        String status = RowSupport.strValue(route, "status");
        if (!"RECOMMENDED".equals(status) && !"SELECTED".equals(status)) {
            throw new ApiException(HttpStatus.CONFLICT, "이 경로는 더 이상 선택할 수 없습니다.");
        }

        expireSelections(planId, phase, routeMemberId, routeId);
        jdbc.sql("""
                UPDATE travel_routes
                SET status = 'SELECTED', selected_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(routeId)
                .update();
        return routeView(routeById(tripId, routeId));
    }

    private List<OptionSpec> optionSpecs(RoutePhase phase) {
        return switch (phase) {
            case DEPARTURE -> List.of(
                    new OptionSpec("fast", "가장 빠른 출발", "FASTEST", 1.0, false,
                            "예상 이동 시간이 가장 짧은 출발안입니다.",
                            "이동 시간을 우선한 예상 구간입니다."),
                    new OptionSpec("easy", "편안한 출발", "FEWER_TRANSFERS", 1.15, true,
                            "환승 부담과 대기 상황을 고려한 출발안입니다.",
                            "환승 부담을 줄이고 대기 여유를 둔 예상 구간입니다."));
            case IN_TRIP -> List.of(
                    new OptionSpec("balanced", "균형 동선", "BALANCED", 1.0, false,
                            "일정 순서에 따라 이동 시간과 환승 횟수를 계산한 동선입니다.",
                            "이동 시간과 환승을 함께 고려한 예상 구간입니다."),
                    new OptionSpec("crowd", "한적한 동선", "CROWD_BUFFER", 1.15, false,
                            "혼잡 가능성에 대비해 이동 여유 시간을 둔 동선입니다.",
                            "혼잡 가능성에 대비한 여유 시간을 포함한 예상 구간입니다."));
            case RETURN -> List.of(
                    new OptionSpec("home-fast", "빠른 귀가", "FASTEST", 1.0, false,
                            "마지막 일정 뒤 바로 이동하는 귀가안입니다.",
                            "이동 시간을 우선한 예상 구간입니다."),
                    new OptionSpec("home-rest", "여유로운 귀가", "REST_BUFFER", 1.2, false,
                            "휴식과 대기 시간을 고려해 여유를 둔 귀가안입니다.",
                            "휴식과 대기 여유를 포함한 예상 구간입니다."));
        };
    }

    @Transactional
    public void clearSelectionForUser(
            long tripId, long userId, RoutePhase phase, Long requestedUserId) {
        trips.requireMember(tripId, userId);
        Map<String, Object> trip = trips.requireTrip(tripId);
        long actorMemberId = participantIdForUser(tripId, userId);
        Long memberId = resolveMemberId(
                trip, phase, requestedUserId, userId, actorMemberId);
        long planId = getPlanId(tripId, phase);
        lockPlan(planId);

        JdbcClient.StatementSpec statement = jdbc.sql(memberId == null ? """
                UPDATE travel_routes
                SET status = 'CANCELED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id = ? AND phase = ? AND member_id IS NULL AND status = 'SELECTED'
                """ : """
                UPDATE travel_routes
                SET status = 'CANCELED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id = ? AND phase = ? AND member_id = ? AND status = 'SELECTED'
                """);
        if (memberId == null) {
            statement.params(planId, phase.name()).update();
        } else {
            statement.params(planId, phase.name(), memberId).update();
        }
    }

    public List<Map<String, Object>> selectionsForUser(long tripId, long userId) {
        trips.requireMember(tripId, userId);
        long actorMemberId = participantIdForUser(tripId, userId);
        return jdbc.sql("""
                SELECT r.id, r.plan_id, p.trip_id, r.member_id, r.phase, r.route_data,
                       r.transport_mode, r.duration_minutes, r.distance_meters,
                       r.transfer_count, r.fare, r.status, r.recommended_at, r.selected_at,
                       participant.user_id AS member_user_id
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                LEFT JOIN trip_participants participant ON participant.id = r.member_id
                WHERE p.trip_id = ? AND r.status = 'SELECTED'
                  AND (r.member_id IS NULL OR r.member_id = ?)
                ORDER BY r.selected_at DESC, r.id DESC
                """)
                .params(tripId, actorMemberId)
                .query().listOfRows().stream()
                .map(this::routeView)
                .toList();
    }

    /** 교통 중단 등으로 더 이상 유효하지 않은 여행의 활성 경로를 모두 만료시킨다. */
    @Transactional
    public int expireActiveForTrip(long tripId) {
        trips.requireTrip(tripId);
        return jdbc.sql("""
                UPDATE travel_routes
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id IN (SELECT id FROM travel_plans WHERE trip_id = ?)
                  AND status IN ('RECOMMENDED', 'SELECTED')
                """)
                .param(tripId)
                .update();
    }

    public RoutePhase routePhase(String type) {
        if (type == null || type.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "경로 종류를 골라 주세요.");
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "DEPARTURE" -> RoutePhase.DEPARTURE;
            case "ITINERARY", "IN_TRIP" -> RoutePhase.IN_TRIP;
            case "HOME", "RETURN" -> RoutePhase.RETURN;
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "올바르지 않은 경로 종류입니다.");
        };
    }

    private RouteContext departureContext(Map<String, Object> trip, Map<String, Object> member) {
        DepartureMode mode = DepartureMode.valueOf(RowSupport.strValue(trip, "departure_mode"));
        long tripId = RowSupport.longValue(trip, "id");
        Location firstPlace = placeLocation(plans.firstPlace(tripId));

        if (mode == DepartureMode.TOGETHER && member == null) {
            Long meetingPlaceId = nullableLong(trip, "meeting_place_id");
            if (meetingPlaceId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "집결 장소가 설정되지 않았습니다.");
            }
            return RouteContext.of(
                    placeLocation(getPlace(meetingPlaceId)), firstPlace, "GROUP");
        }
        if (member == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "개별 출발 경로에는 참여자 식별자가 필요합니다.");
        }
        Long departurePlaceId = nullableLong(member, "departure_place_id");
        if (departurePlaceId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "출발 장소가 설정되지 않았습니다.");
        }
        Location origin = placeLocation(getPlace(departurePlaceId));
        Location destination;
        if (mode == DepartureMode.TOGETHER) {
            Long meetingPlaceId = nullableLong(trip, "meeting_place_id");
            if (meetingPlaceId == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "집결 장소가 설정되지 않았습니다.");
            }
            destination = placeLocation(getPlace(meetingPlaceId));
        } else {
            destination = firstPlace;
        }
        return RouteContext.of(origin, destination, "MEMBER");
    }

    private RouteContext returnContext(long tripId, Map<String, Object> member) {
        if (member == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "귀가 경로에는 참여자 식별자가 필요합니다.");
        }
        Long returnPlaceId = nullableLong(member, "return_place_id");
        if (returnPlaceId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "귀가 장소가 설정되지 않았습니다.");
        }
        return RouteContext.of(
                placeLocation(plans.lastPlace(tripId)),
                placeLocation(getPlace(returnPlaceId)),
                "MEMBER"
        );
    }

    private RouteContext itineraryContext(long tripId) {
        List<Location> stops = itineraryStops(tripId);
        if (stops.size() < 2) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "여행 동선을 계산하려면 장소 일정이 두 개 이상 필요합니다.");
        }
        return new RouteContext(stops, "GROUP");
    }

    private List<Location> itineraryStops(long tripId) {
        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT p.name, p.latitude, p.longitude
                FROM travel_plans tp
                JOIN travel_plan_items i ON i.plan_id = tp.id
                JOIN places p ON p.id = i.place_id AND p.status = 'ACTIVE'
                WHERE tp.trip_id = ? AND tp.status != 'CANCELED'
                ORDER BY tp.day_number, i.sequence_no, i.id
                LIMIT ?
                """)
                .params(tripId, MAX_ITINERARY_STOPS + 1)
                .query().listOfRows();
        if (rows.size() > MAX_ITINERARY_STOPS) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "여행 동선은 장소 일정 100개까지 계산할 수 있습니다.");
        }
        List<Location> stops = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            stops.add(placeLocation(row));
        }
        return List.copyOf(stops);
    }

    private String routeRevision(long tripId, Long memberId) {
        String schedule = jdbc.sql("""
                SELECT p.id AS plan_id, p.version, p.day_number,
                       i.id AS item_id, i.place_id, i.sequence_no,
                       place.name AS place_name, place.latitude, place.longitude,
                       place.updated_at AS place_updated_at
                FROM travel_plans p
                LEFT JOIN travel_plan_items i ON i.plan_id = p.id
                LEFT JOIN places place ON place.id = i.place_id
                WHERE p.trip_id = ? AND p.status != 'CANCELED'
                ORDER BY p.day_number, p.id, i.sequence_no, i.id
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .map(row -> RowSupport.longValue(row, "plan_id") + ":"
                        + RowSupport.intValue(row, "version") + ":"
                        + RowSupport.intValue(row, "day_number") + ":"
                        + String.valueOf(nullableValue(row, "item_id")) + ":"
                        + String.valueOf(nullableValue(row, "place_id")) + ":"
                        + String.valueOf(nullableValue(row, "sequence_no")) + ":"
                        + String.valueOf(nullableValue(row, "place_name")) + ":"
                        + String.valueOf(nullableValue(row, "latitude")) + ":"
                        + String.valueOf(nullableValue(row, "longitude")) + ":"
                        + String.valueOf(nullableValue(row, "place_updated_at")))
                .collect(java.util.stream.Collectors.joining("|"));
        String trip = jdbc.sql("""
                SELECT departure_mode, meeting_place_id, version
                FROM trips WHERE id = ? AND deleted_at IS NULL
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .map(row -> RowSupport.strValue(row, "departure_mode") + ":"
                        + String.valueOf(nullableValue(row, "meeting_place_id")) + ":"
                        + RowSupport.intValue(row, "version"))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
        String member = memberId == null ? "GROUP" : jdbc.sql("""
                SELECT departure_place_id, return_place_id, updated_at
                FROM trip_participants WHERE id = ? AND status = 'JOINED'
                """)
                .param(memberId)
                .query().listOfRows().stream()
                .findFirst()
                .map(row -> String.valueOf(nullableValue(row, "departure_place_id")) + ":"
                        + String.valueOf(nullableValue(row, "return_place_id")) + ":"
                        + String.valueOf(nullableValue(row, "updated_at")))
                .orElse("MISSING");
        return trip + "|" + member + "|" + schedule;
    }

    private String normalizedOptionId(RoutePhase phase, String requestedOptionId) {
        if (requestedOptionId == null || requestedOptionId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "선택할 경로 번호나 선택안 값을 보내 주세요.");
        }
        String optionId = requestedOptionId.trim().toLowerCase(Locale.ROOT);
        boolean allowed = switch (phase) {
            case DEPARTURE -> optionId.equals("fast") || optionId.equals("easy");
            case IN_TRIP -> optionId.equals("balanced") || optionId.equals("crowd");
            case RETURN -> optionId.equals("home-fast") || optionId.equals("home-rest");
        };
        if (!allowed) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "경로 종류에 맞는 선택안 값을 보내 주세요.");
        }
        return optionId;
    }

    @SuppressWarnings("unchecked")
    private long routeIdForOption(
            long planId, RoutePhase phase, Long memberId, String optionId) {
        JdbcClient.StatementSpec statement = jdbc.sql(memberId == null ? """
                SELECT id, route_data FROM travel_routes
                WHERE plan_id = ? AND phase = ? AND member_id IS NULL
                  AND status IN ('RECOMMENDED', 'SELECTED')
                ORDER BY id DESC
                """ : """
                SELECT id, route_data FROM travel_routes
                WHERE plan_id = ? AND phase = ? AND member_id = ?
                  AND status IN ('RECOMMENDED', 'SELECTED')
                ORDER BY id DESC
                """);
        List<Map<String, Object>> candidates = memberId == null
                ? statement.params(planId, phase.name()).query().listOfRows()
                : statement.params(planId, phase.name(), memberId).query().listOfRows();
        for (Map<String, Object> candidate : candidates) {
            Object rawData = nullableValue(candidate, "route_data");
            if (rawData == null) continue;
            Map<String, Object> data = json.read(rawData.toString(), Map.class);
            if (optionId.equals(data.get("optionId"))) {
                return RowSupport.longValue(candidate, "id");
            }
        }
        throw new ApiException(HttpStatus.NOT_FOUND,
                "선택할 수 있는 추천 경로를 찾지 못했습니다. 경로를 다시 추천받아 주세요.");
    }

    private Long resolveMemberId(Map<String, Object> trip, RoutePhase phase,
                                 Long requestedUserId, long actorUserId, long actorMemberId) {
        if (requestedUserId != null && requestedUserId != actorUserId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "자신의 경로만 조회하거나 바꿀 수 있습니다.");
        }
        if (requestedUserId != null) {
            if (phase == RoutePhase.IN_TRIP) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "여행 동선은 참여자 번호 없이 여행 전체 기준으로 요청해 주세요.");
            }
            return actorMemberId;
        }
        if (phase == RoutePhase.RETURN) return actorMemberId;
        if (phase == RoutePhase.DEPARTURE
                && DepartureMode.SEPARATE.name().equals(RowSupport.strValue(trip, "departure_mode"))) {
            return actorMemberId;
        }
        return null;
    }

    private long getPlanId(long tripId, RoutePhase phase) {
        String order = phase == RoutePhase.RETURN ? "DESC" : "ASC";
        return jdbc.sql("""
                SELECT id FROM travel_plans
                WHERE trip_id = ? AND status != 'CANCELED'
                ORDER BY day_number %s LIMIT 1
                """.formatted(order))
                .param(tripId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "경로 계산 전에 일정이 필요합니다."));
    }

    private Map<String, Object> lockTrip(long tripId) {
        return jdbc.sql("""
                SELECT * FROM trips
                WHERE id = ? AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다."));
    }

    private void lockPlan(long planId) {
        jdbc.sql("SELECT id FROM travel_plans WHERE id = ? FOR UPDATE")
                .param(planId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));
    }

    private long routePlanId(long tripId, long routeId) {
        return jdbc.sql("""
                SELECT r.plan_id
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                WHERE r.id = ? AND p.trip_id = ?
                """)
                .params(routeId, tripId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "추천 경로를 찾을 수 없습니다."));
    }

    private Map<String, Object> lockedRoute(long routeId, long planId) {
        return jdbc.sql("""
                SELECT id, plan_id, member_id, phase, status
                FROM travel_routes WHERE id = ? AND plan_id = ? FOR UPDATE
                """)
                .params(routeId, planId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "추천 경로를 찾을 수 없습니다."));
    }

    private Map<String, Object> routeById(long tripId, long routeId) {
        return jdbc.sql("""
                SELECT r.id, r.plan_id, p.trip_id, r.member_id, r.phase, r.route_data,
                       r.transport_mode, r.duration_minutes, r.distance_meters,
                       r.transfer_count, r.fare, r.status, r.recommended_at, r.selected_at,
                       participant.user_id AS member_user_id
                FROM travel_routes r
                JOIN travel_plans p ON p.id = r.plan_id
                LEFT JOIN trip_participants participant ON participant.id = r.member_id
                WHERE r.id = ? AND p.trip_id = ?
                """)
                .params(routeId, tripId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "추천 경로를 찾을 수 없습니다."));
    }

    private void expireActiveRoutes(long planId, RoutePhase phase, Long memberId) {
        if (memberId == null) {
            jdbc.sql("""
                    UPDATE travel_routes SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE plan_id = ? AND phase = ? AND member_id IS NULL
                      AND status IN ('RECOMMENDED', 'SELECTED')
                    """)
                    .params(planId, phase.name())
                    .update();
        } else {
            jdbc.sql("""
                    UPDATE travel_routes SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE plan_id = ? AND phase = ? AND member_id = ?
                      AND status IN ('RECOMMENDED', 'SELECTED')
                    """)
                    .params(planId, phase.name(), memberId)
                    .update();
        }
    }

    private void expireSelections(long planId, RoutePhase phase, Long memberId, long exceptRouteId) {
        if (memberId == null) {
            jdbc.sql("""
                    UPDATE travel_routes SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE plan_id = ? AND phase = ? AND member_id IS NULL
                      AND status = 'SELECTED' AND id != ?
                    """)
                    .params(planId, phase.name(), exceptRouteId)
                    .update();
        } else {
            jdbc.sql("""
                    UPDATE travel_routes SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                    WHERE plan_id = ? AND phase = ? AND member_id = ?
                      AND status = 'SELECTED' AND id != ?
                    """)
                    .params(planId, phase.name(), memberId, exceptRouteId)
                    .update();
        }
    }

    private long participantIdForUser(long tripId, long userId) {
        return jdbc.sql("""
                SELECT id FROM trip_participants
                WHERE trip_id = ? AND user_id = ? AND status = 'JOINED'
                """)
                .params(tripId, userId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "여행 참여자만 처리할 수 있습니다."));
    }

    private Map<String, Object> member(long tripId, long memberId) {
        return jdbc.sql("""
                SELECT id, user_id, departure_place_id, return_place_id
                FROM trip_participants
                WHERE trip_id = ? AND id = ? AND status = 'JOINED'
                """)
                .params(tripId, memberId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "여행 참여자를 찾을 수 없습니다."));
    }

    private Map<String, Object> getPlace(long placeId) {
        return jdbc.sql("""
                SELECT id, name, latitude, longitude
                FROM places WHERE id = ? AND status = 'ACTIVE'
                """)
                .param(placeId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "경로에 필요한 장소를 찾을 수 없습니다."));
    }

    private Location placeLocation(Map<String, Object> place) {
        return new Location(
                RowSupport.strValue(place, "name"),
                ((Number) RowSupport.value(place, "latitude")).doubleValue(),
                ((Number) RowSupport.value(place, "longitude")).doubleValue()
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> routeView(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", RowSupport.longValue(row, "id"));
        result.put("tripId", RowSupport.longValue(row, "trip_id"));
        result.put("planId", RowSupport.longValue(row, "plan_id"));
        putMemberContract(
                result,
                nullableLong(row, "member_id"),
                nullableLong(row, "member_user_id"));
        RoutePhase phase = RoutePhase.valueOf(RowSupport.strValue(row, "phase"));
        result.put("type", apiType(phase));
        result.put("phase", phase.name());
        result.put("transportMode", RowSupport.strValue(row, "transport_mode"));
        putIfPresent(result, "durationMinutes", row, "duration_minutes");
        putIfPresent(result, "distanceMeters", row, "distance_meters");
        putIfPresent(result, "transferCount", row, "transfer_count");
        putIfPresent(result, "fare", row, "fare");
        result.put("status", RowSupport.strValue(row, "status"));
        putIfPresent(result, "recommendedAt", row, "recommended_at");
        putIfPresent(result, "selectedAt", row, "selected_at");
        Object routeData = nullableValue(row, "route_data");
        if (routeData != null) {
            Map<String, Object> data = json.read(routeData.toString(), Map.class);
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
        if (value != null) target.put(targetKey, value);
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
        return jdbc.sql("SELECT user_id FROM trip_participants WHERE id = ?")
                .param(participantId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.CONFLICT, "경로의 참여자 정보를 찾을 수 없습니다."));
    }

    private String apiType(RoutePhase phase) {
        return switch (phase) {
            case DEPARTURE -> "DEPARTURE";
            case IN_TRIP -> "ITINERARY";
            case RETURN -> "HOME";
        };
    }

    private void putIfPresent(Map<String, Object> target, String targetKey,
                              Map<String, Object> row, String rowKey) {
        Object value = nullableValue(row, rowKey);
        if (value != null) target.put(targetKey, value);
    }

    private Object nullableValue(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private Long nullableLong(Map<String, Object> row, String key) {
        Object value = nullableValue(row, key);
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.parseLong(value.toString());
    }

    private record RouteContext(List<Location> stops, String scope) {
        private RouteContext {
            stops = List.copyOf(stops);
            if (stops.size() < 2) {
                throw new IllegalArgumentException("경로에는 장소가 두 곳 이상 필요합니다.");
            }
        }

        private static RouteContext of(Location origin, Location destination, String scope) {
            return new RouteContext(List.of(origin, destination), scope);
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
            RouteProvider.RouteEstimate estimate) {
    }

    private record RecommendationPreparation(
            long tripId,
            RoutePhase phase,
            Long memberId,
            Long authenticatedUserId,
            RouteContext context,
            String routeRevision) {
    }

    private record OptionSpec(
            String id,
            String name,
            String strategy,
            double durationFactor,
            boolean fewerTransfers,
            String summary,
            String segmentSummary) {
    }

    private record RouteCalculation(
            RouteContext context,
            List<SegmentEstimate> segments,
            int durationMinutes,
            int transferCount,
            int fare,
            String providerName) {
    }
}
