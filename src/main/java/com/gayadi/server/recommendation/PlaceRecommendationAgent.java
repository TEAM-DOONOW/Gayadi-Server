package com.gayadi.server.recommendation;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** TourAPI 후보를 검색하고 Groq가 최종 선택하도록 조정하는 API-first Agent입니다. */
@Service
@ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
public class PlaceRecommendationAgent {

    private static final Logger log = LoggerFactory.getLogger(PlaceRecommendationAgent.class);

    private static final int MAX_MODEL_CANDIDATES = 20;
    private static final int MIN_CANDIDATES_BEFORE_REFINEMENT = 3;
    private static final int LARGE_GROUP_SIZE = 6;
    private static final double BASE_SCORE = 0.45;
    private static final double INDOOR_MATCH_BONUS = 0.25;
    private static final double MAX_DISTANCE_BONUS = 0.20;
    private static final double DISTANCE_SCORE_DIVISOR = 100.0;
    private static final double MAX_SCORED_DISTANCE_KM = 20.0;
    private static final double KEYWORD_MATCH_BONUS = 0.05;
    private static final double LARGE_GROUP_CATEGORY_BONUS = 0.05;
    private static final double TRANSIT_NEARBY_BONUS = 0.08;
    private static final double TRANSIT_NEARBY_DISTANCE_KM = 5.0;

    private final RecommendationLanguageModel languageModel;
    private final TourPlaceSearchGateway searchGateway;
    private final PlaceSnapshotWriter snapshotWriter;

    @Autowired
    public PlaceRecommendationAgent(RecommendationLanguageModel languageModel,
                                    TourPlaceSearchGateway searchGateway,
                                    PlaceSnapshotWriter snapshotWriter) {
        this.languageModel = languageModel;
        this.searchGateway = searchGateway;
        this.snapshotWriter = snapshotWriter;
    }

    public PlaceRecommendationAgent(RecommendationLanguageModel languageModel,
                                    TourPlaceSearchGateway searchGateway) {
        this(languageModel, searchGateway, (candidates, destination) -> Map.of());
    }

    public PlaceRecommendationResponse recommendPlaces(PlaceRecommendationRequest request) {
        RecommendationLanguageModel.RecommendationContext context = context(request);
        PlaceSearchPlan plan = safePlan(context);
        List<TourPlaceCandidate> candidates = rankedCandidates(
                searchGateway.search(plan, searchContext(request, context.policy())), context);

        if (candidates.isEmpty()) {
            PlaceSearchPlan fallback = PlaceSearchPlan.fallback(
                    context.destination(), context.regionCode(), context.sigunguCode(),
                    context.keywords(), context.policy());
            candidates = rankedCandidates(
                    searchGateway.search(fallback, searchContext(request, context.policy())), context);
        }

        if (candidates.size() < Math.min(context.limit(), MIN_CANDIDATES_BEFORE_REFINEMENT)
                && plan.maxSearchRounds() > 1) {
            try {
                PlaceSearchPlan refined = languageModel.refineSearchPlan(context, plan, candidates);
                candidates = mergeAndRank(candidates,
                        searchGateway.search(refined, searchContext(request, context.policy())), context);
            } catch (RuntimeException exception) {
                log.warn("추천 검색 계획 개선을 생략합니다: {}", exception.getClass().getSimpleName());
            }
        }

        if (candidates.isEmpty()) {
            return new PlaceRecommendationResponse(List.of(), noCandidateReason(context));
        }

        List<TourPlaceCandidate> allowed = candidates.stream().limit(MAX_MODEL_CANDIDATES).toList();
        RecommendationLanguageModel.CandidateDecision decision = safeDecision(context, allowed);
        Map<String, TourPlaceCandidate> byId = allowed.stream()
                .collect(Collectors.toMap(TourPlaceCandidate::placeId, value -> value,
                        (first, ignored) -> first, LinkedHashMap::new));
        Map<String, RecommendationLanguageModel.CandidateDecision.Selection> selections =
                validSelections(decision, byId, context.limit());
        List<TourPlaceCandidate> selectedCandidates = selections.isEmpty()
                ? allowed.stream().limit(context.limit()).toList()
                : selections.keySet().stream().map(byId::get).toList();
        Map<String, Long> localIds = snapshotWriter.save(selectedCandidates, context.destination());
        List<RecommendedPlace> recommendations;
        if (selections.isEmpty()) {
            recommendations = selectedCandidates.stream()
                    .map(candidate -> fallbackRecommendation(candidate, context, localIds))
                    .toList();
        } else {
            recommendations = selections.values().stream()
                    .map(selection -> recommended(
                            selection, byId.get(selection.placeId()), context, localIds))
                    .toList();
        }
        String reasoning = decision == null ? "" : decision.reasoning();
        return new PlaceRecommendationResponse(recommendations,
                reasoning.isBlank() ? context.policy().summary() : reasoning);
    }

    private Map<String, RecommendationLanguageModel.CandidateDecision.Selection> validSelections(
            RecommendationLanguageModel.CandidateDecision decision,
            Map<String, TourPlaceCandidate> candidates,
            int limit) {
        Map<String, RecommendationLanguageModel.CandidateDecision.Selection> result =
                new LinkedHashMap<>();
        if (decision == null) return result;
        for (RecommendationLanguageModel.CandidateDecision.Selection selection
                : decision.recommendations()) {
            if (selection == null || !candidates.containsKey(selection.placeId())) continue;
            result.putIfAbsent(selection.placeId(), selection);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private RecommendedPlace fallbackRecommendation(
            TourPlaceCandidate candidate,
            RecommendationLanguageModel.RecommendationContext context,
            Map<String, Long> localIds) {
        return new RecommendedPlace(
                localPlaceId(candidate, localIds), candidate.name(), candidate.category(),
                candidateScore(candidate, context), "검색 조건에 맞는 후보 장소입니다.",
                candidate.placeId());
    }

    private String noCandidateReason(
            RecommendationLanguageModel.RecommendationContext context) {
        String prefix = context.policy().indoorRequired() ? "실내 조건을 만족하는" : "조건에 맞는";
        return prefix + " 공개 관광 후보를 찾지 못했습니다.";
    }

    private RecommendedPlace recommended(
            RecommendationLanguageModel.CandidateDecision.Selection selection,
            TourPlaceCandidate candidate,
            RecommendationLanguageModel.RecommendationContext context,
            Map<String, Long> localIds) {
        double score = selection.score() == null || !Double.isFinite(selection.score())
                ? candidateScore(candidate, context)
                : Math.max(0, Math.min(1, selection.score()));
        String localId = localIds.containsKey(candidate.placeId())
                ? String.valueOf(localIds.get(candidate.placeId())) : candidate.placeId();
        return new RecommendedPlace(localId, candidate.name(),
                candidate.category(), score,
                selection.reason().isBlank() ? "여행 조건에 맞는 후보입니다." : selection.reason(),
                candidate.placeId());
    }

    private String localPlaceId(TourPlaceCandidate candidate, Map<String, Long> localIds) {
        Long localId = localIds.get(candidate.placeId());
        return localId == null ? candidate.placeId() : String.valueOf(localId);
    }

    private PlaceSearchPlan safePlan(RecommendationLanguageModel.RecommendationContext context) {
        try {
            PlaceSearchPlan plan = languageModel.createSearchPlan(context);
            if (plan != null && !plan.queries().isEmpty()) return plan;
        } catch (RuntimeException exception) {
            log.warn("추천 검색 계획 생성에 실패해 기본 계획을 사용합니다: {}",
                    exception.getClass().getSimpleName());
        }
        return PlaceSearchPlan.fallback(context.destination(), context.regionCode(),
                context.sigunguCode(), context.keywords(), context.policy());
    }

    private RecommendationLanguageModel.CandidateDecision safeDecision(
            RecommendationLanguageModel.RecommendationContext context,
            List<TourPlaceCandidate> candidates) {
        try {
            return languageModel.decide(context, candidates);
        } catch (RuntimeException exception) {
            log.warn("추천 후보 판정에 실패해 서버 순위를 사용합니다: {}",
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private RecommendationLanguageModel.RecommendationContext context(
            PlaceRecommendationRequest request) {
        TravelSituation situation = request.getSituation();
        return new RecommendationLanguageModel.RecommendationContext(
                request.getPurpose(), request.getDestination(), request.getRegionCode(),
                request.getSigunguCode(), request.getProfile(),
                request.getKeywords() == null ? List.of() : request.getKeywords(),
                request.getLatitude(), request.getLongitude(), request.getGroupSize(),
                request.getLimit(), request.getTargetAt(), situation, situation.policy());
    }

    private TourPlaceSearchGateway.SearchContext searchContext(
            PlaceRecommendationRequest request, TravelSituation.Policy policy) {
        return new TourPlaceSearchGateway.SearchContext(
                request.getRegionCode(), request.getSigunguCode(),
                request.getLatitude(), request.getLongitude(), policy);
    }

    private List<TourPlaceCandidate> mergeAndRank(
            List<TourPlaceCandidate> first,
            List<TourPlaceCandidate> second,
            RecommendationLanguageModel.RecommendationContext context) {
        Map<String, TourPlaceCandidate> merged = new LinkedHashMap<>();
        first.forEach(candidate -> merged.put(candidate.placeId(), candidate));
        second.forEach(candidate -> merged.putIfAbsent(candidate.placeId(), candidate));
        return rankedCandidates(List.copyOf(merged.values()), context);
    }

    private List<TourPlaceCandidate> rankedCandidates(
            List<TourPlaceCandidate> candidates,
            RecommendationLanguageModel.RecommendationContext context) {
        if (candidates == null) return List.of();
        return candidates.stream()
                .filter(candidate -> candidate != null && !candidate.placeId().isBlank())
                .filter(candidate -> !context.policy().indoorRequired()
                        || candidate.matchesIndoorRequirement())
                .distinct()
                .sorted(Comparator.comparingDouble(
                        candidate -> -candidateScore(candidate, context)))
                .toList();
    }

    private double candidateScore(TourPlaceCandidate candidate,
                                  RecommendationLanguageModel.RecommendationContext context) {
        double score = BASE_SCORE;
        if (context.policy().indoorRequired() && candidate.matchesIndoorRequirement()) {
            score += INDOOR_MATCH_BONUS;
        }
        if (candidate.distanceKm() != null) {
            score += Math.max(0, MAX_DISTANCE_BONUS
                    - Math.min(candidate.distanceKm(), MAX_SCORED_DISTANCE_KM)
                    / DISTANCE_SCORE_DIVISOR);
        }
        String text = (candidate.name() + " " + candidate.category() + " "
                + candidate.description()).toLowerCase(Locale.ROOT);
        for (String keyword : context.keywords()) {
            if (keyword != null && !keyword.isBlank()
                    && text.contains(keyword.toLowerCase(Locale.ROOT))) {
                score += KEYWORD_MATCH_BONUS;
            }
        }
        if (context.groupSize() >= LARGE_GROUP_SIZE
                && Set.of("CULTURE", "SHOPPING", "RESTAURANT").contains(candidate.category())) {
            score += LARGE_GROUP_CATEGORY_BONUS;
        }
        if (context.policy().transitDisrupted() && candidate.distanceKm() != null
                && candidate.distanceKm() <= TRANSIT_NEARBY_DISTANCE_KM) {
            score += TRANSIT_NEARBY_BONUS;
        }
        return Math.max(0, Math.min(1, score));
    }
}
