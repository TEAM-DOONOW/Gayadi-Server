package com.gayadi.server.event;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.event.dto.response.ChangeProposalOptionResponse;
import com.gayadi.server.event.dto.response.ChangeProposalResponse;
import com.gayadi.server.event.dto.response.EventObservationResponse;
import com.gayadi.server.event.dto.response.EventObservationResult;
import com.gayadi.server.event.command.AiChangeProposalCommand;
import com.gayadi.server.event.command.AiChangeProposalOption;
import com.gayadi.server.event.command.ChangeProposalDecision;
import com.gayadi.server.event.command.EventObservationCommand;
import com.gayadi.server.event.model.ChangeProposalStatus;
import com.gayadi.server.event.model.ChangeProposalType;
import com.gayadi.server.event.model.Severity;
import com.gayadi.server.event.query.AlternativePlaceQueryResult;
import com.gayadi.server.event.query.ChangeProposalOptionQueryResult;
import com.gayadi.server.event.query.ChangeProposalQueryResult;
import com.gayadi.server.event.query.EventPlanQueryResult;
import com.gayadi.server.event.query.EventTripQueryResult;
import com.gayadi.server.schedule.PlanService;
import com.gayadi.server.travel.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** 현장 상황의 영향도를 판단하고 일정 변경 제안의 생성·승인 규칙을 수행합니다. */
@Service
public class EventService {

    private static final String PRIMARY_SHELTER_OPTION = "INDOOR_SHELTER";
    private static final String AI_SITUATION_SOURCE = "AI_SITUATION_AGENT";
    private static final int MAX_PROPOSAL_REASON_LENGTH = 1000;
    private static final int MAX_OPTION_DESCRIPTION_LENGTH = 500;
    private static final int MAX_PROPOSAL_OPTIONS = 5;
    private static final int SITUATION_VALID_HOURS = 2;

    private final EventRepository repository;
    private final TripService trips;
    private final PlanService plans;
    private final JsonSupport json;

    public EventService(
            EventRepository repository,
            TripService trips,
            PlanService plans,
            JsonSupport json) {
        this.repository = repository;
        this.trips = trips;
        this.plans = plans;
        this.json = json;
    }

    /** 여행 중 상황을 기록하고 필요한 변경 제안을 생성합니다. */
    @Transactional
    public EventObservationResult observe(long tripId, EventObservationCommand command) {
        validateObservation(command);
        String normalizedData = ObservationPayloadValidator.validateAndSerialize(
                command.values(),
                json);
        EventTripQueryResult trip = repository.lockInProgressTrip(tripId);
        requirePlaceInRegion(command.placeId(), trip.regionId());
        Object planSnapshot = plans.get(tripId);
        EventPlanQueryResult targetPlan = repository.findCurrentPlan(tripId);

        long eventId = repository.insertObservation(
                command.placeId(),
                trip.regionId(),
                command.eventType().name(),
                command.source(),
                LocalDateTime.now().plusHours(SITUATION_VALID_HOURS),
                command.severity().name(),
                normalizedData);

        if (command.severity() == Severity.LOW) {
            return new EventObservationResponse(
                    eventId,
                    false,
                    "일정 변경이 필요하지 않습니다.");
        }

        List<ChangeProposalOptionQueryResult> options = shelterOptions(
                tripId,
                trip.regionId(),
                command.placeId());
        if (options.isEmpty()) {
            throw new BusinessException(EventErrorCode.EVENT_INDOOR_ALTERNATIVE_NOT_FOUND);
        }

        long proposalId = repository.insertProposal(
                tripId,
                targetPlan.id(),
                eventId,
                ChangeProposalType.fromEventType(command.eventType().name()).name(),
                reason(command),
                planSnapshot,
                null,
                targetPlan.version(),
                options);
        return proposal(proposalId);
    }

    /** AI 추천 결과를 검증해 일정 변경 제안으로 저장합니다. */
    @Transactional
    public Optional<ChangeProposalResponse> proposeFromAgent(
            long tripId,
            AiChangeProposalCommand command) {
        EventTripQueryResult trip = repository.lockInProgressTrip(tripId);
        List<ChangeProposalOptionQueryResult> options = agentOptions(
                tripId,
                trip.regionId(),
                command.options(),
                command.requireIndoor());
        if (options.isEmpty()) {
            return Optional.empty();
        }

        Object planSnapshot = plans.get(tripId);
        EventPlanQueryResult targetPlan = repository.findCurrentPlan(tripId);
        long eventId = repository.insertObservation(
                null,
                trip.regionId(),
                command.proposalType().eventType(),
                AI_SITUATION_SOURCE,
                LocalDateTime.now().plusHours(SITUATION_VALID_HOURS),
                Severity.HIGH.name(),
                json.write(command.situationData()));

        repository.expirePendingAgentProposals(
                tripId,
                targetPlan.id(),
                targetPlan.version(),
                AI_SITUATION_SOURCE);

        long proposalId = repository.insertProposal(
                tripId,
                targetPlan.id(),
                eventId,
                command.proposalType().name(),
                limit(command.reason(), MAX_PROPOSAL_REASON_LENGTH),
                planSnapshot,
                LocalDateTime.now().plusHours(SITUATION_VALID_HOURS),
                targetPlan.version(),
                options);
        return Optional.of(proposal(proposalId));
    }

    /** 여행의 변경 제안 목록을 기본 페이지 조건으로 조회합니다. */
    public List<ChangeProposalResponse> proposals(long tripId) {
        return proposals(tripId, 100, 0);
    }

    /** 여행의 변경 제안 목록을 페이지 조건에 맞춰 조회합니다. */
    public List<ChangeProposalResponse> proposals(
            long tripId,
            int requestedLimit,
            int requestedOffset) {
        trips.requireTrip(tripId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        int offset = Math.max(0, requestedOffset);
        return repository.findAll(tripId, limit, offset)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 여행에서 아직 결정되지 않은 변경 제안을 조회합니다. */
    public List<ChangeProposalResponse> pendingProposals(long tripId, int requestedLimit) {
        trips.requireTrip(tripId);
        int limit = Math.max(1, Math.min(requestedLimit, 100));
        return repository.findPending(tripId, limit)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** 변경 제안에 대한 참여자의 결정을 반영합니다. */
    @Transactional
    public ChangeProposalResponse decide(
            long tripId,
            long proposalId,
            ChangeProposalDecision command) {
        trips.requireMember(tripId, command.decidedBy());
        repository.lockInProgressTrip(tripId);
        ChangeProposalQueryResult proposal = repository.lockProposal(tripId, proposalId);
        if (!ChangeProposalStatus.PENDING.name().equals(proposal.status())) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_ALREADY_DECIDED);
        }
        if (proposal.baseRevisionNo() == null
                || command.baseRevisionNo() != proposal.baseRevisionNo()) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_REVISION_MISMATCH);
        }

        // 거절은 일정 데이터를 변경하지 않고 제안 상태만 원자적으로 확정합니다.
        if (!command.approve()) {
            if (!repository.reject(tripId, proposalId, command.decidedBy())) {
                throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_ALREADY_DECIDED);
            }
            return proposal(proposalId);
        }

        ChangeProposalOptionQueryResult selected = selectedOption(
                proposal,
                command.selectedOptionKey());

        // 제안 생성 이후 장소나 일정이 바뀌었을 수 있으므로 적용 직전에 다시 검증합니다.
        ensureActiveAlternative(
                tripId,
                selected.placeId(),
                selected.requireIndoor());

        if (proposal.planId() == null) {
            throw new BusinessException(EventErrorCode.EVENT_PLAN_NOT_FOUND);
        }
        EventPlanQueryResult currentPlan = repository.lockPlan(tripId, proposal.planId());
        if (currentPlan.version() != proposal.baseRevisionNo()) {
            throw new BusinessException(EventErrorCode.EVENT_PLAN_ALREADY_CHANGED);
        }
        if (!repository.incrementPlanVersion(
                tripId,
                proposal.planId(),
                proposal.baseRevisionNo())) {
            throw new BusinessException(EventErrorCode.EVENT_PLAN_ALREADY_CHANGED);
        }
        if (!repository.updateNextPlanItem(
                proposal.planId(),
                selected.placeId(),
                selected.placeName())) {
            throw new BusinessException(
                    EventErrorCode.EVENT_SCHEDULE_CHANGE_TARGET_NOT_FOUND);
        }

        // 기존 제안과 경로는 이전 일정 버전을 기준으로 하므로 함께 만료시킵니다.
        repository.expireSiblingProposals(
                tripId,
                proposal.planId(),
                proposal.baseRevisionNo(),
                proposalId);
        repository.expireActiveRoutes(tripId);

        Object after = plans.get(tripId);
        if (!repository.approve(
                tripId,
                proposalId,
                selected.key(),
                command.decidedBy(),
                after)) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_ALREADY_DECIDED);
        }
        return proposal(proposalId);
    }

    private List<ChangeProposalOptionQueryResult> shelterOptions(
            long tripId,
            long regionId,
            Long observedPlaceId) {
        List<AlternativePlaceQueryResult> places = repository.findShelterAlternatives(
                tripId,
                regionId,
                observedPlaceId);
        List<ChangeProposalOptionQueryResult> options = new ArrayList<>();
        for (int index = 0; index < places.size(); index++) {
            AlternativePlaceQueryResult place = places.get(index);
            options.add(new ChangeProposalOptionQueryResult(
                    index == 0
                            ? PRIMARY_SHELTER_OPTION
                            : PRIMARY_SHELTER_OPTION + "_" + place.id(),
                    place.id(),
                    place.name(),
                    place.name() + "으로 다음 일정을 변경합니다.",
                    true));
        }
        return options;
    }

    private List<ChangeProposalOptionQueryResult> agentOptions(
            long tripId,
            long regionId,
            List<AiChangeProposalOption> requested,
            boolean requireIndoor) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        List<ChangeProposalOptionQueryResult> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (AiChangeProposalOption option : requested) {
            if (option == null || !seen.add(option.placeId())) {
                continue;
            }
            AlternativePlaceQueryResult place = repository.findAvailablePlace(
                    tripId,
                    regionId,
                    option.placeId(),
                    requireIndoor);
            if (place == null) {
                continue;
            }
            result.add(new ChangeProposalOptionQueryResult(
                    "AI_RECOMMENDATION_" + place.id(),
                    place.id(),
                    place.name(),
                    limit(option.description(), MAX_OPTION_DESCRIPTION_LENGTH),
                    requireIndoor));
            if (result.size() >= MAX_PROPOSAL_OPTIONS) {
                break;
            }
        }
        return result;
    }

    private void ensureActiveAlternative(
            long tripId,
            long placeId,
            boolean requireIndoor) {
        if (!repository.isActiveAlternative(tripId, placeId, requireIndoor)) {
            String label = requireIndoor ? "실내 대체 장소" : "대체 장소";
            throw new BusinessException(
                    EventErrorCode.EVENT_ALTERNATIVE_PLACE_UNAVAILABLE,
                    label);
        }
    }

    private void requirePlaceInRegion(Long placeId, long regionId) {
        if (placeId != null && !repository.isPlaceInRegion(placeId, regionId)) {
            throw new BusinessException(EventErrorCode.OBSERVATION_PLACE_OUTSIDE_REGION);
        }
    }

    private ChangeProposalOptionQueryResult selectedOption(
            ChangeProposalQueryResult proposal,
            String selectedKey) {
        if (selectedKey == null || selectedKey.isBlank()) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_OPTION_REQUIRED);
        }
        if (proposal.options() == null || proposal.options().isEmpty()) {
            throw new BusinessException(EventErrorCode.CHANGE_PROPOSAL_OPTIONS_MISSING);
        }
        return proposal.options()
                .stream()
                .filter(option -> selectedKey.equals(option.key()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        EventErrorCode.CHANGE_PROPOSAL_OPTION_NOT_ALLOWED));
    }

    private ChangeProposalResponse proposal(long id) {
        return toResponse(repository.findProposal(id));
    }

    private ChangeProposalResponse toResponse(ChangeProposalQueryResult proposal) {
        List<ChangeProposalOptionResponse> options = proposal.options()
                .stream()
                .map(option -> new ChangeProposalOptionResponse(
                        option.key(),
                        option.placeId(),
                        option.placeName(),
                        option.description(),
                        option.requireIndoor()))
                .toList();
        return new ChangeProposalResponse(
                proposal.id(),
                proposal.tripId(),
                proposal.planId(),
                proposal.eventId(),
                proposal.type(),
                proposal.reason(),
                proposal.status(),
                proposal.baseRevisionNo(),
                options,
                proposal.selectedOptionKey(),
                proposal.decidedBy(),
                proposal.generatedAt(),
                proposal.decidedAt(),
                proposal.appliedAt(),
                proposal.before(),
                proposal.after());
    }

    private String reason(EventObservationCommand command) {
        return command.eventType().label()
                + " 상황이 "
                + command.severity().label()
                + " 단계로 확인되었습니다.";
    }

    private void validateObservation(EventObservationCommand command) {
        if (command.source().length() > 50) {
            throw new BusinessException(EventErrorCode.OBSERVATION_SOURCE_TOO_LONG);
        }
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "AI가 현재 상황에 맞는 대체 장소를 추천했습니다.";
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, maxLength);
    }
}
