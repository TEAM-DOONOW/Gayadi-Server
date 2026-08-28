package com.gayadi.server.recommendation;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.event.ChangeProposalType;
import com.gayadi.server.event.EventService;
import com.gayadi.server.route.RouteService;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.travel.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "상황 대처")
@SecurityRequirement(name = "bearerAuth")
public class TripSituationController {

    private final ObjectProvider<SituationResponseAgent> agentProvider;
    private final TripService trips;
    private final SurveyService surveys;
    private final RouteService routes;
    private final EventService events;
    private final WeatherSituationEnricher weather;

    public TripSituationController(ObjectProvider<SituationResponseAgent> agentProvider,
                                   TripService trips,
                                   SurveyService surveys,
                                   RouteService routes,
                                   EventService events,
                                   WeatherSituationEnricher weather) {
        this.agentProvider = agentProvider;
        this.trips = trips;
        this.surveys = surveys;
        this.routes = routes;
        this.events = events;
        this.weather = weather;
    }

    @PostMapping("/situation-responses")
    @Operation(summary = "여행 상황 대처",
            description = "여행의 참여자·성향과 날씨·혼잡·교통 상황을 반영해 대체 장소와 다음 조치를 제안합니다. "
                    + "날씨를 생략하면 현재 위치의 기상청 초단기실황을 자동 적용하고, 여행 중에는 승인 가능한 변경안을 생성합니다.")
    public SituationResponse respond(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody TripSituationRequest request) {
        trips.requireMember(tripId, userId);
        SituationResponseAgent agent = agentProvider.getIfAvailable();
        if (agent == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "상황 대처 Agent가 설정되지 않았습니다.");
        }
        Map<String, Object> trip = trips.view(tripId);
        List<?> cities = valueAsList(trip.get("cities"));
        List<?> members = valueAsList(trip.get("participantIds"));

        PlaceRecommendationRequest recommendation = new PlaceRecommendationRequest();
        recommendation.setDestination(cities.isEmpty() ? "" : String.valueOf(cities.getFirst()));
        recommendation.setRegionCode(request.getRegionCode());
        recommendation.setSigunguCode(request.getSigunguCode());
        recommendation.setProfile(groupProfile(tripId));
        recommendation.setLatitude(request.getLatitude());
        recommendation.setLongitude(request.getLongitude());
        recommendation.setKeywords(request.getKeywords());
        recommendation.setLimit(request.getLimit());
        recommendation.setGroupSize(Math.max(1, members.size()));
        recommendation.setTargetAt(request.getTargetAt());
        TravelSituation effectiveSituation = weather.enrich(
                request.getSituation(), request.getLatitude(), request.getLongitude());
        request.setSituation(effectiveSituation);
        recommendation.setSituation(effectiveSituation);
        recommendation.setExternalProcessingConsent(request.isExternalProcessingConsent());
        SituationResponse response = agent.respond(recommendation);
        if ("ONGOING".equals(String.valueOf(trip.get("status")))) {
            Map<String, Object> proposal = createChangeProposal(tripId, request, response);
            if (!proposal.isEmpty()) response = response.withChangeProposal(proposal);
        }
        if (response.routeRecalculationRequired()) {
            routes.expireActiveForTrip(tripId);
        }
        return response;
    }

    private Map<String, Object> createChangeProposal(
            long tripId,
            TripSituationRequest request,
            SituationResponse response) {
        TravelSituation.Policy policy = request.getSituation().policy();
        ChangeProposalType proposalType = proposalType(policy);
        if (proposalType == null) return Map.of();

        List<EventService.AiOption> options = response.placeRecommendations().recommendations()
                .stream()
                .map(place -> aiOption(place, response.placeRecommendations().reasoning()))
                .filter(Objects::nonNull)
                .toList();
        if (options.isEmpty()) return Map.of();

        Map<String, Object> situationData = new LinkedHashMap<>();
        situationData.put("summary", response.situationSummary());
        situationData.put("weather", request.getSituation().weather());
        situationData.put("congestion", request.getSituation().congestion());
        situationData.put("transit", request.getSituation().transit());
        return events.proposeFromAgent(tripId, new EventService.AiProposal(
                proposalType,
                response.situationSummary() + ": " + response.nextAction(),
                situationData,
                options,
                policy.indoorRequired()));
    }

    private EventService.AiOption aiOption(RecommendedPlace place, String reasoning) {
        try {
            long placeId = Long.parseLong(place.placeId());
            String description = place.reason().isBlank() ? reasoning : place.reason();
            return new EventService.AiOption(placeId, description);
        } catch (NumberFormatException ignored) {
            // 내부 장소 ID로 저장되지 않은 외부 후보는 승인 가능한 제안에서 제외한다.
            return null;
        }
    }

    private ChangeProposalType proposalType(TravelSituation.Policy policy) {
        if (policy.transitDisrupted()) return ChangeProposalType.TRANSPORT_CHANGE;
        if (policy.indoorRequired() || policy.avoidOutdoor()) {
            return ChangeProposalType.WEATHER_CHANGE;
        }
        if (policy.avoidCrowded()) return ChangeProposalType.CONGESTION_CHANGE;
        return null;
    }

    private String groupProfile(long tripId) {
        Map<String, Object> profile = surveys.groupProfile(tripId);
        return "그룹 대표 성향: " + profile.getOrDefault("dominantProfile", "UNKNOWN")
                + ", 성향 분포: " + profile.getOrDefault("distribution", Map.of());
    }

    private List<?> valueAsList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }
}
