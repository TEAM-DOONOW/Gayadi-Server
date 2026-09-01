package com.gayadi.server.survey;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.exception.CommonErrorCode;
import com.gayadi.server.survey.dto.request.SurveyResponseItem;
import com.gayadi.server.survey.dto.response.GroupPersonalityResponse;
import com.gayadi.server.survey.dto.response.PersonalityResultResponse;
import com.gayadi.server.survey.dto.response.SurveyResponse;
import com.gayadi.server.survey.dto.response.SurveySubmissionResponse;
import com.gayadi.server.survey.query.SurveyQueryResults.ActiveQuestion;
import com.gayadi.server.survey.query.SurveyQueryResults.Attempt;
import com.gayadi.server.survey.query.SurveyQueryResults.Header;
import com.gayadi.server.survey.query.SurveyQueryResults.PersonalityResult;
import com.gayadi.server.survey.query.SurveyQueryResults.ProfileCount;
import com.gayadi.server.survey.query.SurveyQueryResults.QuestionOption;
import com.gayadi.server.travel.TripService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 여행 성향 설문 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class SurveyService {

    public static final long PERSONALITY_SURVEY_ID = 2L;

    private final SurveyRepository repository;
    private final TripService trips;
    private final JsonSupport json;

    public SurveyService(SurveyRepository repository, TripService trips, JsonSupport json) {
        this.repository = repository;
        this.trips = trips;
        this.json = json;
    }

    /** 설문 관련 성향 설문 업무를 처리합니다. */
    public SurveyResponse personalitySurvey() {
        Header survey = repository.findActiveSurvey(PERSONALITY_SURVEY_ID)
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_ACTIVE_NOT_FOUND));
        Map<Long, MutableQuestion> questions = new LinkedHashMap<>();
        for (QuestionOption row : repository.findQuestionOptions(PERSONALITY_SURVEY_ID)) {
            MutableQuestion question = questions.computeIfAbsent(row.questionId(), ignored -> new MutableQuestion(
                    apiQuestionId(row.questionSequence()), row.questionText(), dimension(row.axisType()),
                    row.questionSequence(), new ArrayList<>()));
            if (row.optionId() != null) {
                question.options().add(new SurveyResponse.Option(
                        apiOptionId(row.optionSequence()),
                        row.optionText(),
                        row.optionCode()));
            }
        }
        return new SurveyResponse(
                "travel-personality-v1",
                survey.name(),
                survey.description(),
                survey.version(),
                survey.status().toLowerCase(Locale.ROOT),
                List.of("preparation", "place", "energy"),
                questions.values().stream()
                        .map(MutableQuestion::response)
                        .toList(),
                repository.findAllResults().stream()
                        .map(this::resultResponse)
                        .toList());
    }

    /** 설문 응답을 채점하고 개인 또는 여행 그룹 성향 결과를 저장합니다. */
    @Transactional
    public SurveySubmissionResponse respond(
            Long tripId,
            long userId,
            List<SurveyResponseItem> responses) {
        if (tripId != null) {
            trips.requireMember(tripId, userId);
        }
        if (!repository.lockActiveUser(userId)) {
            throw new BusinessException(CommonErrorCode.UNAUTHENTICATED);
        }
        Map<String, SelectedOption> selectedOptions = validateAndLoadResponses(responses);
        int prepScore = 0;
        int placeScore = 0;
        int styleScore = 0;
        for (SurveyResponseItem response : responses) {
            SelectedOption option = selectedOptions.get(response.getQuestionId());
            switch (option.axisType()) {
                case "TRAVEL_PREPARATION" -> prepScore += option.scoreValue();
                case "PLACE_PREFERENCE" -> placeScore += option.scoreValue();
                case "TRAVEL_STYLE" -> styleScore += option.scoreValue();
                default -> throw new BusinessException(SurveyErrorCode.SURVEY_QUESTION_CATEGORY_INVALID);
            }
        }
        String prepType = prepScore >= 0 ? "PLANNED" : "SPONTANEOUS";
        String placePref = placeScore >= 0 ? "NATURE" : "CITY";
        String style = styleScore >= 0 ? "ACTIVE" : "RELAXED";
        String resultCode = "" + prepType.charAt(0) + placePref.charAt(0) + style.charAt(0);
        repository.deletePreviousAttempt(tripId, userId, PERSONALITY_SURVEY_ID);
        long attemptId = repository.createAttempt(userId, PERSONALITY_SURVEY_ID, tripId,
                prepScore, placeScore, styleScore, prepType, placePref, style, resultCode);
        for (SurveyResponseItem response : responses) {
            SelectedOption selected = selectedOptions.get(response.getQuestionId());
            repository.addResponse(attemptId, selected.questionId(), selected.optionId(), selected.scoreValue());
        }
        return response(attemptId);
    }

    /** 결과에 대한 성향 설문 기능을 처리합니다. */
    public PersonalityResultResponse result(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(SurveyErrorCode.SURVEY_RESULT_CODE_REQUIRED);
        }
        return repository.findResult(code.trim().toUpperCase(Locale.ROOT)).map(this::resultResponse)
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_RESULT_NOT_FOUND));
    }

    /** 성향 관련 성향 설문 업무를 처리합니다. */
    public GroupPersonalityResponse groupProfile(long tripId) {
        trips.requireTrip(tripId);
        List<ProfileCount> profiles = repository.findProfileCounts(tripId, PERSONALITY_SURVEY_ID);
        if (profiles.isEmpty()) {
            throw new BusinessException(SurveyErrorCode.SURVEY_PROFILE_REQUIRED);
        }
        Map<String, Long> distribution = new LinkedHashMap<>();
        profiles.forEach(profile -> distribution.put(profile.resultCode(), profile.responseCount()));
        long responseCount = profiles.stream().mapToLong(ProfileCount::responseCount).sum();
        return new GroupPersonalityResponse(
                profiles.getFirst().resultCode(),
                responseCount,
                distribution);
    }

    /** 여행에서 가장 최근에 제출된 성향 응답 번호를 조회합니다. */
    public long latestResponseId(long tripId) {
        return repository.findLatestAttemptId(tripId, PERSONALITY_SURVEY_ID)
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_RESPONSE_REQUIRED));
    }

    private SurveySubmissionResponse response(long attemptId) {
        Attempt attempt = repository.findAttempt(attemptId)
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_RESPONSE_NOT_FOUND));
        return new SurveySubmissionResponse(
                attempt.id(), attempt.tripId(), attempt.resultCode(), attempt.preparationScore(),
                attempt.placeScore(), attempt.energyScore(), result(attempt.resultCode()));
    }

    private Map<String, SelectedOption> validateAndLoadResponses(List<SurveyResponseItem> responses) {
        if (responses == null || responses.isEmpty()) {
            throw new BusinessException(SurveyErrorCode.SURVEY_ALL_ANSWERS_REQUIRED,
                    repository.activeQuestionCount(PERSONALITY_SURVEY_ID));
        }
        if (responses.stream().anyMatch(response -> response == null
                || response.getQuestionId() == null || response.getOptionId() == null)) {
            throw new BusinessException(SurveyErrorCode.SURVEY_ANSWER_IDS_REQUIRED);
        }
        List<ActiveQuestion> activeQuestions = repository.findActiveQuestions(PERSONALITY_SURVEY_ID);
        if (activeQuestions.isEmpty()) {
            throw new BusinessException(SurveyErrorCode.SURVEY_UNAVAILABLE);
        }
        int requiredCount = activeQuestions.size();
        long distinctQuestions = responses.stream().map(SurveyResponseItem::getQuestionId).distinct().count();
        if (responses.size() != requiredCount || distinctQuestions != requiredCount) {
            throw new BusinessException(SurveyErrorCode.SURVEY_ANSWER_COUNT_INVALID, requiredCount);
        }
        Map<String, ActiveQuestion> activeById = new LinkedHashMap<>();
        activeQuestions.forEach(question -> activeById.put(apiQuestionId(question.sequence()), question));
        Map<String, com.gayadi.server.survey.query.SurveyQueryResults.Option> optionById = new LinkedHashMap<>();
        repository.findOptions(PERSONALITY_SURVEY_ID).forEach(option -> optionById.put(
                apiQuestionId(option.questionSequence()) + ":" + apiOptionId(option.optionSequence()), option));
        Map<String, SelectedOption> selected = new LinkedHashMap<>();
        for (SurveyResponseItem response : responses) {
            ActiveQuestion question = activeById.get(response.getQuestionId());
            if (question == null) {
                throw new BusinessException(SurveyErrorCode.SURVEY_QUESTION_INACTIVE);
            }
            var option = optionById.get(response.getQuestionId() + ":" + response.getOptionId());
            if (option == null || option.questionId() != question.questionId()) {
                throw new BusinessException(SurveyErrorCode.SURVEY_OPTION_MISMATCH);
            }
            selected.put(response.getQuestionId(), new SelectedOption(
                    question.questionId(), option.optionId(),
                    question.axisType(), option.scoreValue()));
        }
        return selected;
    }

    private PersonalityResultResponse resultResponse(PersonalityResult result) {
        List<Map<String, Object>> compatible = jsonListOfMaps(result.compatibleTypesJson());
        List<PersonalityResultResponse.CompatibleType> types = compatible.stream()
                .map(value -> new PersonalityResultResponse.CompatibleType(
                        text(value, "code"), text(value, "emoji"), text(value, "name")))
                .toList();

        Map<String, Object> role = jsonMap(result.travelRoleJson());
        return new PersonalityResultResponse(
                result.resultCode(),
                result.emoji(),
                result.name(),
                result.summary(),
                result.characterKey(),
                jsonStringList(result.hashtagsJson()),
                jsonStringList(result.strengthsJson()),
                jsonStringList(result.weaknessesJson()),
                types,
                new PersonalityResultResponse.TravelRole(
                        text(role, "icon"),
                        text(role, "title"),
                        text(role, "description")));
    }

    private String dimension(String axisType) {
        return switch (axisType) {
            case "TRAVEL_PREPARATION" -> "preparation";
            case "PLACE_PREFERENCE" -> "place";
            case "TRAVEL_STYLE" -> "energy";
            default -> throw new BusinessException(SurveyErrorCode.SURVEY_QUESTION_CATEGORY_INVALID);
        };
    }

    private String apiQuestionId(int sequence) {
        return "q%02d".formatted(sequence);
    }

    private String apiOptionId(int sequence) {
        if (sequence < 1 || sequence > 26) {
            throw new BusinessException(SurveyErrorCode.SURVEY_OPTION_SEQUENCE_INVALID);
        }
        return String.valueOf((char) ('a' + sequence - 1));
    }

    @SuppressWarnings("unchecked")
    private List<String> jsonStringList(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : (List<String>) json.read(value, List.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> jsonListOfMaps(String value) {
        return value == null || value.isBlank()
                ? List.of()
                : (List<Map<String, Object>>) (List<?>) json.read(value, List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> jsonMap(String value) {
        return value == null || value.isBlank() ? Map.of() : (Map<String, Object>) json.read(value, Map.class);
    }

    private String text(Map<String, Object> value, String key) {
        Object result = value.get(key);
        return result == null ? null : result.toString();
    }

    private record MutableQuestion(
            String id,
            String title,
            String dimension,
            int order,
            List<SurveyResponse.Option> options
    ) {
        private SurveyResponse.Question response() {
            return new SurveyResponse.Question(
                    id,
                    title,
                    dimension,
                    order,
                    List.copyOf(options));
        }
    }

    private record SelectedOption(
            long questionId,
            long optionId,
            String axisType,
            int scoreValue
    ) {
    }
}
