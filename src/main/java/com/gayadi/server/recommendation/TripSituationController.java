package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.dto.request.PlaceRecommendationRequest;
import com.gayadi.server.recommendation.dto.request.TripSituationRequest;
import com.gayadi.server.recommendation.dto.response.RecommendedPlace;
import com.gayadi.server.recommendation.dto.response.SituationResponse;
import com.gayadi.server.recommendation.dto.response.SituationChangeProposalResponse;
import com.gayadi.server.recommendation.model.TravelSituation;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.response.ApiErrorResponse;
import com.gayadi.server.event.EventService;
import com.gayadi.server.event.command.AiChangeProposalCommand;
import com.gayadi.server.event.command.AiChangeProposalOption;
import com.gayadi.server.event.model.ChangeProposalType;
import com.gayadi.server.route.RouteService;
import com.gayadi.server.survey.SurveyErrorCode;
import com.gayadi.server.survey.SurveyService;
import com.gayadi.server.survey.dto.response.GroupPersonalityResponse;
import com.gayadi.server.travel.TripService;
import com.gayadi.server.travel.dto.response.TripResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
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

/** 진행 중 여행의 상황 대응과 변경 제안 생성 요청을 처리합니다. */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "상황 대처", description = "여행 상황 대처 Agent. APP_AI_ENABLED=true 필요")
@SecurityRequirement(name = "bearerAuth")
public class TripSituationController {

    private final ObjectProvider<SituationResponseAgent> agentProvider;
    private final TripService trips;
    private final SurveyService surveys;
    private final RouteService routes;
    private final EventService events;
    private final WeatherSituationEnricher weather;
    private final CongestionSituationEnricher congestion;

    public TripSituationController(ObjectProvider<SituationResponseAgent> agentProvider,
                                   TripService trips,
                                   SurveyService surveys,
                                   RouteService routes,
                                   EventService events,
                                   WeatherSituationEnricher weather,
                                   CongestionSituationEnricher congestion) {
        this.agentProvider = agentProvider;
        this.trips = trips;
        this.surveys = surveys;
        this.routes = routes;
        this.events = events;
        this.weather = weather;
        this.congestion = congestion;
    }

    @PostMapping("/situation-responses")
    @Operation(summary = "여행 상황 대처",
            description = "여행 참여자·성향과 날씨·혼잡·교통을 반영해 대체 장소와 다음 조치를 제안합니다. "
                    + "설문이 없으면 기본 성향으로 진행합니다. "
                    + "날씨·혼잡을 생략하면 기상청 초단기실황과 관광지 집중률로 보강하고, 보강이 실패해도 Agent는 계속됩니다. "
                    + "여행 중(ONGOING)이면 승인 가능한 변경안을 만들고, 승인·거절은 "
                    + "`PATCH /api/v1/trips/{tripId}/change-proposals/{proposalId}`입니다. "
                    + "APP_AI_ENABLED=false이면 503 SITUATION_AGENT_UNAVAILABLE입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "여행 상황 대처 및 변경안 생성 성공",
                    content = @Content(schema = @Schema(implementation = SituationResponse.class))),
            @ApiResponse(responseCode = "400", description = "상황 값 검증 실패 또는 외부 처리 미동의",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "로그인 토큰 누락 또는 만료",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "여행 참여자가 아님",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "여행을 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "상황 대처 Agent가 꺼져 있습니다. SITUATION_AGENT_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SituationResponse respond(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody TripSituationRequest request) {
        trips.requireMember(tripId, userId);
        SituationResponseAgent agent = agentProvider.getIfAvailable();
        if (agent == null) {
            throw new BusinessException(RecommendationErrorCode.SITUATION_AGENT_UNAVAILABLE);
        }
        TripResponse trip = trips.view(tripId);
        List<String> cities = trip.cities();
        List<Long> members = trip.participantIds();

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
        effectiveSituation = congestion.enrich(effectiveSituation,
                request.getRegionCode(), request.getSigunguCode(), request.getTargetAt());
        request.setSituation(effectiveSituation);
        recommendation.setSituation(effectiveSituation);
        recommendation.setExternalProcessingConsent(request.isExternalProcessingConsent());
        SituationResponse response = agent.respond(recommendation);
        if (isOngoing(trip.status())) {
            SituationChangeProposalResponse proposal = createChangeProposal(tripId, request, response);
            if (proposal.id() != null) {
                response = response.withChangeProposal(proposal);
            }
        }
        if (response.routeRecalculationRequired()) {
            routes.expireActiveForTrip(tripId);
        }
        return response;
    }

    private SituationChangeProposalResponse createChangeProposal(
            long tripId,
            TripSituationRequest request,
            SituationResponse response) {
        TravelSituation.Policy policy = request.getSituation().policy();
        ChangeProposalType proposalType = proposalType(policy);
        if (proposalType == null) {
            return SituationChangeProposalResponse.empty();
        }

        List<AiChangeProposalOption> options = response.placeRecommendations().recommendations()
                .stream()
                .map(place -> aiOption(place, response.placeRecommendations().reasoning()))
                .filter(Objects::nonNull)
                .toList();
        if (options.isEmpty()) {
            return SituationChangeProposalResponse.empty();
        }

        Map<String, Object> situationData = new LinkedHashMap<>();
        situationData.put("summary", response.situationSummary());
        situationData.put("weather", request.getSituation().weather());
        situationData.put("congestion", request.getSituation().congestion());
        situationData.put("transit", request.getSituation().transit());
        return events.proposeFromAgent(tripId, new AiChangeProposalCommand(
                proposalType,
                response.situationSummary() + ": " + response.nextAction(),
                situationData,
                options,
                policy.indoorRequired()))
                .map(SituationChangeProposalResponse::from)
                .orElseGet(SituationChangeProposalResponse::empty);
    }

    private AiChangeProposalOption aiOption(RecommendedPlace place, String reasoning) {
        try {
            long placeId = Long.parseLong(place.placeId());
            String description = place.reason().isBlank() ? reasoning : place.reason();
            return new AiChangeProposalOption(placeId, description);
        } catch (NumberFormatException ignored) {
            // 내부 장소 ID로 저장되지 않은 외부 후보는 승인 가능한 제안에서 제외한다.
            return null;
        }
    }

    private ChangeProposalType proposalType(TravelSituation.Policy policy) {
        if (policy.transitDisrupted()) {
            return ChangeProposalType.TRANSPORT_CHANGE;
        }
        if (policy.indoorRequired() || policy.avoidOutdoor()) {
            return ChangeProposalType.WEATHER_CHANGE;
        }
        if (policy.avoidCrowded()) {
            return ChangeProposalType.CONGESTION_CHANGE;
        }
        return null;
    }

    private boolean isOngoing(String status) {
        return "ONGOING".equals(status) || "IN_PROGRESS".equals(status);
    }

    private String groupProfile(long tripId) {
        try {
            GroupPersonalityResponse profile = surveys.groupProfile(tripId);
            return "그룹 대표 성향: " + profile.dominantProfile()
                    + ", 성향 분포: " + profile.distribution();
        } catch (BusinessException exception) {
            if (exception.getErrorCode() == SurveyErrorCode.SURVEY_PROFILE_REQUIRED) {
                return "여행 성향 설문이 아직 없습니다.";
            }
            throw exception;
        }
    }

}
