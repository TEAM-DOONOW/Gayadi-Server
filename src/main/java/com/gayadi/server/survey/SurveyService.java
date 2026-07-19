package com.gayadi.server.survey;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.travel.TripService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SurveyService {
    private static final String PERSONALITY_SURVEY_ID = "10000000-0000-0000-0000-000000000001";

    private final JdbcClient jdbc;
    private final TripService trips;
    private final JsonSupport json;

    public SurveyService(JdbcClient jdbc, TripService trips, JsonSupport json) {
        this.jdbc = jdbc;
        this.trips = trips;
        this.json = json;
    }

    public Map<String, Object> personalitySurvey() {
        return jdbc.sql("SELECT id, survey_type, version, questions, status FROM surveys WHERE id = ?")
                .param(PERSONALITY_SURVEY_ID).query().singleRow();
    }

    @Transactional
    public Map<String, Object> respond(String tripId, String userId, Map<String, Integer> answers) {
        trips.requireMember(tripId, userId);
        validateAnswers(answers);
        String resultCode = calculateResult(answers);
        String id = UUID.randomUUID().toString();
        Map<String, Object> resultData = Map.of("profile", resultCode, "scoreTotal",
                answers.values().stream().mapToInt(Integer::intValue).sum());
        jdbc.sql("DELETE FROM survey_responses WHERE trip_id = ? AND survey_id = ? AND user_id = ?")
                .params(tripId, PERSONALITY_SURVEY_ID, userId).update();
        jdbc.sql("""
                INSERT INTO survey_responses(id, survey_id, user_id, trip_id, answers, result_code, result_data)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """).params(id, PERSONALITY_SURVEY_ID, userId, tripId, json.write(answers), resultCode,
                        json.write(resultData)).update();
        return response(id);
    }

    public Map<String, Object> groupProfile(String tripId) {
        trips.requireTrip(tripId);
        List<String> profiles = jdbc.sql("SELECT result_code FROM survey_responses WHERE trip_id = ?")
                .param(tripId).query(String.class).list();
        if (profiles.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "일정 생성 전에 한 명 이상 성향 설문을 제출해야 합니다.");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        profiles.forEach(profile -> counts.merge(profile, 1L, Long::sum));
        String dominant = counts.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
        return Map.of("dominantProfile", dominant, "responseCount", profiles.size(), "distribution", counts);
    }

    public String latestResponseId(String tripId) {
        return jdbc.sql("SELECT id FROM survey_responses WHERE trip_id = ? ORDER BY created_at DESC LIMIT 1")
                .param(tripId).query(String.class).optional()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "성향 설문 응답이 필요합니다."));
    }

    private Map<String, Object> response(String id) {
        return jdbc.sql("SELECT * FROM survey_responses WHERE id = ?").param(id).query().singleRow();
    }

    private void validateAnswers(Map<String, Integer> answers) {
        if (!answers.keySet().containsAll(List.of("pace", "indoor", "food"))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "pace, indoor, food 답변이 모두 필요합니다.");
        }
        if (answers.values().stream().anyMatch(score -> score == null || score < 1 || score > 5)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "설문 점수는 1부터 5 사이여야 합니다.");
        }
    }

    private String calculateResult(Map<String, Integer> answers) {
        if (answers.get("indoor") >= 4) return "INDOOR";
        if (answers.get("pace") >= 4) return "ACTIVE";
        if (answers.get("food") >= 4) return "FOODIE";
        return "BALANCED";
    }
}
