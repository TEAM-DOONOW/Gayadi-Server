package com.gayadi.server.survey;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.exception.CommonErrorCode;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.travel.TripService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class SurveyService {

    /** Android의 9문항 설문 버전. 이전 3문항 설문과 응답은 별도로 보존한다. */
    public static final long PERSONALITY_SURVEY_ID = 2L;

    private final JdbcClient jdbc;
    private final TripService trips;
    private final JsonSupport json;
    private final KeyHelper keyHelper;

    public SurveyService(JdbcClient jdbc, TripService trips, JsonSupport json, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.json = json;
        this.keyHelper = keyHelper;
    }

    public Map<String, Object> personalitySurvey() {
        Map<String, Object> surveyRow = jdbc.sql("""
                SELECT id, name, description, version, status
                FROM surveys
                WHERE id = ? AND status = 'ACTIVE'
                """)
                .param(PERSONALITY_SURVEY_ID)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_ACTIVE_NOT_FOUND));

        List<Map<String, Object>> rows = jdbc.sql("""
                SELECT q.id AS question_id, q.question_text, q.axis_type, q.sequence_no AS question_sequence,
                       o.id AS option_id, o.option_text, o.option_code, o.score_value,
                       o.sequence_no AS option_sequence
                FROM survey_questions q
                LEFT JOIN survey_question_options o ON o.question_id = q.id
                WHERE q.survey_id = ? AND q.status = 'ACTIVE'
                ORDER BY q.sequence_no, o.sequence_no
                """)
                .param(PERSONALITY_SURVEY_ID)
                .query().listOfRows();

        Map<Long, Map<String, Object>> questionsById = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            long questionId = RowSupport.longValue(row, "question_id");
            Map<String, Object> question = questionsById.computeIfAbsent(questionId, ignored -> {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("id", apiQuestionId(RowSupport.intValue(row, "question_sequence")));
                value.put("title", RowSupport.strValue(row, "question_text"));
                value.put("dimension", dimension(RowSupport.strValue(row, "axis_type")));
                value.put("order", RowSupport.intValue(row, "question_sequence"));
                value.put("options", new ArrayList<Map<String, Object>>());
                return value;
            });

            Object optionId = valueOrNull(row, "option_id");
            if (optionId != null) {
                Map<String, Object> option = new LinkedHashMap<>();
                option.put("id", apiOptionId(number(valueOrNull(row, "option_sequence")).intValue()));
                option.put("text", valueOrNull(row, "option_text"));
                option.put("code", valueOrNull(row, "option_code"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> options = (List<Map<String, Object>>) question.get("options");
                options.add(option);
            }
        }

        List<Map<String, Object>> results = jdbc.sql("""
                SELECT result_code, emoji, name, summary, character_key, hashtags,
                       strengths, weaknesses, compatible_types, travel_role
                FROM travel_personality_results
                ORDER BY result_code
                """)
                .query().listOfRows().stream()
                .map(this::resultDetails)
                .toList();

        Map<String, Object> survey = new LinkedHashMap<>();
        survey.put("id", "travel-personality-v1");
        survey.put("title", RowSupport.strValue(surveyRow, "name"));
        survey.put("description", valueOrNull(surveyRow, "description"));
        survey.put("version", RowSupport.intValue(surveyRow, "version"));
        survey.put("status", RowSupport.strValue(surveyRow, "status").toLowerCase(Locale.ROOT));
        survey.put("resultCodeOrder", List.of("preparation", "place", "energy"));
        survey.put("questions", new ArrayList<>(questionsById.values()));
        survey.put("results", results);
        return survey;
    }

    /**
     * 여행을 만들기 전에도 성향 검사를 마칠 수 있도록 여행 식별자를 선택값으로 받는다.
     */
    @Transactional
    public Map<String, Object> respond(Long tripId, long userId, List<SurveyController.ResponseItem> responses) {
        if (tripId != null) {
            trips.requireMember(tripId, userId);
        }
        lockActiveUser(userId);

        Map<String, SelectedOption> selectedOptions = validateAndLoadResponses(responses);
        int prepScore = 0;
        int placeScore = 0;
        int styleScore = 0;

        for (SurveyController.ResponseItem response : responses) {
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

        if (tripId == null) {
            jdbc.sql("DELETE FROM survey_attempts WHERE trip_id IS NULL AND user_id = ? AND survey_id = ?")
                    .params(userId, PERSONALITY_SURVEY_ID)
                    .update();
        } else {
            jdbc.sql("DELETE FROM survey_attempts WHERE trip_id = ? AND user_id = ? AND survey_id = ?")
                    .params(tripId, userId, PERSONALITY_SURVEY_ID)
                    .update();
        }

        long attemptId = keyHelper.insert("""
                INSERT INTO survey_attempts (user_id, survey_id, trip_id, status,
                                             travel_prep_score, place_pref_score, travel_style_score,
                                             preparation_type, place_preference, travel_style, result_code,
                                             started_at, completed_at)
                VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId, PERSONALITY_SURVEY_ID, tripId,
                prepScore, placeScore, styleScore,
                prepType, placePref, style, resultCode);

        for (SurveyController.ResponseItem response : responses) {
            SelectedOption selected = selectedOptions.get(response.getQuestionId());
            jdbc.sql("""
                    INSERT INTO question_responses (attempt_id, question_id, option_id, score_value)
                    VALUES (?, ?, ?, ?)
                    """)
                    .params(attemptId, selected.questionId(), selected.optionId(), selected.scoreValue())
                    .update();
        }

        return response(attemptId);
    }

    public Map<String, Object> result(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(SurveyErrorCode.SURVEY_RESULT_CODE_REQUIRED);
        }
        String normalizedCode = code.trim().toUpperCase(Locale.ROOT);
        Map<String, Object> row = jdbc.sql("""
                SELECT result_code, emoji, name, summary, character_key, hashtags,
                       strengths, weaknesses, compatible_types, travel_role
                FROM travel_personality_results
                WHERE result_code = ?
                """)
                .param(normalizedCode)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_RESULT_NOT_FOUND));
        return resultDetails(row);
    }

    public Map<String, Object> groupProfile(long tripId) {
        trips.requireTrip(tripId);
        List<Map<String, Object>> profileRows = jdbc.sql("""
                SELECT result_code, COUNT(*) AS response_count
                FROM survey_attempts
                WHERE trip_id = ? AND survey_id = ? AND status = 'COMPLETED'
                GROUP BY result_code
                ORDER BY response_count DESC, result_code
                """)
                .params(tripId, PERSONALITY_SURVEY_ID)
                .query().listOfRows();
        if (profileRows.isEmpty()) {
            throw new BusinessException(SurveyErrorCode.SURVEY_PROFILE_REQUIRED);
        }

        Map<String, Long> distribution = new LinkedHashMap<>();
        long responseCount = 0;
        for (Map<String, Object> row : profileRows) {
            long count = RowSupport.longValue(row, "response_count");
            distribution.put(RowSupport.strValue(row, "result_code"), count);
            responseCount += count;
        }
        return Map.of(
                "dominantProfile", RowSupport.strValue(profileRows.getFirst(), "result_code"),
                "responseCount", responseCount,
                "distribution", distribution
        );
    }

    public long latestResponseId(long tripId) {
        return jdbc.sql(
                "SELECT id FROM survey_attempts WHERE trip_id = ? AND survey_id = ? AND status = 'COMPLETED' ORDER BY completed_at DESC LIMIT 1")
                .params(tripId, PERSONALITY_SURVEY_ID)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_RESPONSE_REQUIRED));
    }

    private Map<String, Object> response(long attemptId) {
        Map<String, Object> attempt = jdbc.sql("SELECT * FROM survey_attempts WHERE id = ?")
                .param(attemptId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(SurveyErrorCode.SURVEY_RESPONSE_NOT_FOUND));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("attemptId", RowSupport.longValue(attempt, "id"));
        value.put("tripId", valueOrNull(attempt, "trip_id"));
        value.put("resultCode", RowSupport.strValue(attempt, "result_code"));
        value.put("preparationScore", RowSupport.intValue(attempt, "travel_prep_score"));
        value.put("placeScore", RowSupport.intValue(attempt, "place_pref_score"));
        value.put("energyScore", RowSupport.intValue(attempt, "travel_style_score"));
        value.put("result", result(RowSupport.strValue(attempt, "result_code")));
        return value;
    }

    private void lockActiveUser(long userId) {
        jdbc.sql("""
                SELECT id FROM users
                WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL
                FOR UPDATE
                """)
                .param(userId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new BusinessException(CommonErrorCode.UNAUTHENTICATED));
    }

    private Map<String, SelectedOption> validateAndLoadResponses(List<SurveyController.ResponseItem> responses) {
        if (responses == null || responses.isEmpty()) {
            int count = activeQuestionCount();
            throw new BusinessException(SurveyErrorCode.SURVEY_ALL_ANSWERS_REQUIRED, count);
        }
        if (responses.stream().anyMatch(response -> response == null
                || response.getQuestionId() == null || response.getOptionId() == null)) {
            throw new BusinessException(SurveyErrorCode.SURVEY_ANSWER_IDS_REQUIRED);
        }

        List<Map<String, Object>> activeQuestions = jdbc.sql("""
                SELECT q.id AS question_id, q.axis_type, q.sequence_no AS question_sequence
                FROM surveys s
                JOIN survey_questions q ON q.survey_id = s.id AND q.status = 'ACTIVE'
                WHERE s.id = ? AND s.status = 'ACTIVE'
                ORDER BY q.sequence_no
                """)
                .param(PERSONALITY_SURVEY_ID)
                .query().listOfRows();

        if (activeQuestions.isEmpty()) {
            throw new BusinessException(SurveyErrorCode.SURVEY_UNAVAILABLE);
        }
        int requiredCount = activeQuestions.size();
        long distinctQuestions = responses.stream()
                .map(SurveyController.ResponseItem::getQuestionId)
                .distinct()
                .count();
        if (responses.size() != requiredCount || distinctQuestions != requiredCount) {
            throw new BusinessException(SurveyErrorCode.SURVEY_ANSWER_COUNT_INVALID, requiredCount);
        }

        Map<String, ActiveQuestion> activeQuestionById = new LinkedHashMap<>();
        for (Map<String, Object> row : activeQuestions) {
            String apiId = apiQuestionId(RowSupport.intValue(row, "question_sequence"));
            activeQuestionById.put(apiId, new ActiveQuestion(
                    RowSupport.longValue(row, "question_id"),
                    RowSupport.strValue(row, "axis_type")));
        }

        List<Map<String, Object>> optionRows = jdbc.sql("""
                SELECT o.id AS option_id, o.question_id, o.score_value,
                       o.sequence_no AS option_sequence, q.sequence_no AS question_sequence
                FROM survey_question_options o
                JOIN survey_questions q ON q.id = o.question_id AND q.status = 'ACTIVE'
                JOIN surveys s ON s.id = q.survey_id AND s.status = 'ACTIVE'
                WHERE s.id = ?
                ORDER BY q.sequence_no, o.sequence_no
                """)
                .param(PERSONALITY_SURVEY_ID)
                .query().listOfRows();
        Map<String, LoadedOption> optionById = new LinkedHashMap<>();
        for (Map<String, Object> row : optionRows) {
            String key = apiQuestionId(RowSupport.intValue(row, "question_sequence"))
                    + ":" + apiOptionId(RowSupport.intValue(row, "option_sequence"));
            optionById.put(key, new LoadedOption(
                    RowSupport.longValue(row, "option_id"),
                    RowSupport.longValue(row, "question_id"),
                    RowSupport.intValue(row, "score_value")
            ));
        }

        Map<String, SelectedOption> selected = new LinkedHashMap<>();
        for (SurveyController.ResponseItem response : responses) {
            ActiveQuestion activeQuestion = activeQuestionById.get(response.getQuestionId());
            if (activeQuestion == null) {
                throw new BusinessException(SurveyErrorCode.SURVEY_QUESTION_INACTIVE);
            }
            LoadedOption option = optionById.get(response.getQuestionId() + ":" + response.getOptionId());
            if (option == null || option.questionId() != activeQuestion.questionId()) {
                throw new BusinessException(SurveyErrorCode.SURVEY_OPTION_MISMATCH);
            }
            selected.put(response.getQuestionId(), new SelectedOption(
                    activeQuestion.questionId(),
                    option.optionId(),
                    activeQuestion.axisType(),
                    option.scoreValue()
            ));
        }
        return selected;
    }

    private int activeQuestionCount() {
        long count = jdbc.sql("""
                SELECT COUNT(*)
                FROM survey_questions q
                JOIN surveys s ON s.id = q.survey_id
                WHERE s.id = ? AND s.status = 'ACTIVE' AND q.status = 'ACTIVE'
                """)
                .param(PERSONALITY_SURVEY_ID)
                .query(Long.class)
                .optional()
                .orElse(0L);
        return Math.toIntExact(count);
    }

    private Map<String, Object> resultDetails(Map<String, Object> row) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("code", RowSupport.strValue(row, "result_code"));
        details.put("emoji", valueOrNull(row, "emoji"));
        details.put("name", RowSupport.strValue(row, "name"));
        details.put("summary", RowSupport.strValue(row, "summary"));
        details.put("characterKey", valueOrNull(row, "character_key"));
        details.put("hashtags", jsonValue(row, "hashtags", List.of()));
        details.put("strengths", jsonValue(row, "strengths", List.of()));
        details.put("weaknesses", jsonValue(row, "weaknesses", List.of()));
        details.put("compatibleTypes", jsonValue(row, "compatible_types", List.of()));
        details.put("travelRole", jsonValue(row, "travel_role", Map.of()));
        return details;
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

    private Object jsonValue(Map<String, Object> row, String key, Object fallback) {
        Object value = valueOrNull(row, key);
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return json.read(value.toString(), Object.class);
    }

    private static Object valueOrNull(Map<String, Object> row, String key) {
        if (row.containsKey(key)) return row.get(key);
        return row.get(key.toUpperCase(Locale.ROOT));
    }

    private static Number number(Object value) {
        if (value instanceof Number number) return number;
        return Long.parseLong(value.toString());
    }

    private record ActiveQuestion(long questionId, String axisType) {
    }

    private record SelectedOption(long questionId, long optionId, String axisType, int scoreValue) {
    }

    private record LoadedOption(long optionId, long questionId, int scoreValue) {
    }
}
