package com.gayadi.server.survey;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SurveyService {

    private static final long PERSONALITY_SURVEY_ID = 1L;

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
        Map<String, Object> survey = jdbc.sql("""
                SELECT id, name, description, version, status FROM surveys WHERE id = ?
                """)
                .param(PERSONALITY_SURVEY_ID)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "설문을 찾을 수 없습니다."));

        List<Map<String, Object>> questions = jdbc.sql("""
                SELECT id, question_text, axis_type, sequence_no
                FROM survey_questions
                WHERE survey_id = ? AND status = 'ACTIVE'
                ORDER BY sequence_no
                """)
                .param(PERSONALITY_SURVEY_ID)
                .query().listOfRows();

        List<Map<String, Object>> questionList = new ArrayList<>();
        for (Map<String, Object> q : questions) {
            Map<String, Object> question = new LinkedHashMap<>(q);
            long qId = RowSupport.longValue(q, "id");
            List<Map<String, Object>> options = jdbc.sql("""
                    SELECT id, option_text, option_code, score_value, sequence_no
                    FROM survey_question_options
                    WHERE question_id = ?
                    ORDER BY sequence_no
                    """)
                    .param(qId)
                    .query().listOfRows();
            question.put("options", options);
            questionList.add(question);
        }

        Map<String, Object> result = new LinkedHashMap<>(survey);
        result.put("questions", questionList);
        return result;
    }

    @Transactional
    public Map<String, Object> respond(long tripId, long userId, List<SurveyController.ResponseItem> responses) {
        trips.requireMember(tripId, userId);
        validateResponses(responses);

        int prepScore = 0;
        int placeScore = 0;
        int styleScore = 0;

        for (SurveyController.ResponseItem r : responses) {
            Map<String, Object> optionInfo = jdbc.sql("""
                    SELECT o.score_value, q.axis_type
                    FROM survey_question_options o
                    JOIN survey_questions q ON q.id = o.question_id
                    WHERE o.id = ?
                    """)
                    .param(r.getOptionId())
                    .query().listOfRows().stream()
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "선택지를 찾을 수 없습니다."));

            int score = RowSupport.intValue(optionInfo, "score_value");
            String axisType = RowSupport.strValue(optionInfo, "axis_type");

            switch (axisType) {
                case "TRAVEL_PREPARATION" -> prepScore += score;
                case "PLACE_PREFERENCE" -> placeScore += score;
                case "TRAVEL_STYLE" -> styleScore += score;
            }
        }

        String prepType = prepScore >= 0 ? "PLANNED" : "SPONTANEOUS";
        String placePref = placeScore >= 0 ? "NATURE" : "CITY";
        String style = styleScore >= 0 ? "ACTIVE" : "RELAXED";
        String resultCode = "" + prepType.charAt(0) + placePref.charAt(0) + style.charAt(0);

        jdbc.sql("DELETE FROM survey_attempts WHERE trip_id = ? AND user_id = ? AND survey_id = ?")
                .params(tripId, userId, PERSONALITY_SURVEY_ID)
                .update();

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

        for (SurveyController.ResponseItem r : responses) {
            Integer scoreValue = jdbc.sql("SELECT score_value FROM survey_question_options WHERE id = ?")
                    .param(r.getOptionId())
                    .query(Integer.class)
                    .single();
            jdbc.sql("""
                    INSERT INTO question_responses (attempt_id, question_id, option_id, score_value)
                    VALUES (?, ?, ?, ?)
                    """)
                    .params(attemptId, r.getQuestionId(), r.getOptionId(), scoreValue)
                    .update();
        }

        return response(attemptId);
    }

    public Map<String, Object> groupProfile(long tripId) {
        trips.requireTrip(tripId);
        List<String> profiles = jdbc.sql(
                "SELECT result_code FROM survey_attempts WHERE trip_id = ? AND status = 'COMPLETED'")
                .param(tripId)
                .query(String.class)
                .list();
        if (profiles.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "일정 생성 전에 한 명 이상 성향 설문을 제출해야 합니다.");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String profile : profiles) {
            counts.merge(profile, 1L, Long::sum);
        }
        String dominant = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "성향 분석에 실패했습니다."));
        return Map.of("dominantProfile", dominant, "responseCount", profiles.size(), "distribution", counts);
    }

    public long latestResponseId(long tripId) {
        return jdbc.sql(
                "SELECT id FROM survey_attempts WHERE trip_id = ? AND status = 'COMPLETED' ORDER BY completed_at DESC LIMIT 1")
                .param(tripId)
                .query(Long.class)
                .optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "성향 설문 응답이 필요합니다."));
    }

    private Map<String, Object> response(long attemptId) {
        return jdbc.sql("SELECT * FROM survey_attempts WHERE id = ?")
                .param(attemptId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "설문 응답을 찾을 수 없습니다."));
    }

    private void validateResponses(List<SurveyController.ResponseItem> responses) {
        if (responses.size() != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "3개 문항에 모두 답변해야 합니다.");
        }
        long distinctQuestions = responses.stream()
                .map(SurveyController.ResponseItem::getQuestionId)
                .distinct()
                .count();
        if (distinctQuestions != 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "3개 문항에 모두 답변해야 합니다.");
        }
    }
}
