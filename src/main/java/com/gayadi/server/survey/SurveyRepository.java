package com.gayadi.server.survey;

import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.survey.query.SurveyQueryResults.ActiveQuestion;
import com.gayadi.server.survey.query.SurveyQueryResults.Attempt;
import com.gayadi.server.survey.query.SurveyQueryResults.Header;
import com.gayadi.server.survey.query.SurveyQueryResults.Option;
import com.gayadi.server.survey.query.SurveyQueryResults.PersonalityResult;
import com.gayadi.server.survey.query.SurveyQueryResults.ProfileCount;
import com.gayadi.server.survey.query.SurveyQueryResults.QuestionOption;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 여행 성향 설문 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class SurveyRepository {

    private final JdbcClient jdbc;
    private final KeyHelper keyHelper;

    public SurveyRepository(JdbcClient jdbc, KeyHelper keyHelper) {
        this.jdbc = jdbc;
        this.keyHelper = keyHelper;
    }

    /** 설문 식별자에 해당하는 활성 설문 기본 정보를 조회합니다. */
    public Optional<Header> findActiveSurvey(long surveyId) {
        return jdbc.sql("""
                SELECT id, name, description, version, status FROM surveys
                WHERE id = ? AND status = 'ACTIVE'
                """)
                .param(surveyId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapHeader);
    }

    /** 문항 선택지 목록 조건에 맞는 성향 설문 데이터를 DB에서 조회합니다. */
    public List<QuestionOption> findQuestionOptions(long surveyId) {
        return jdbc.sql("""
                SELECT q.id AS question_id, q.question_text, q.axis_type, q.sequence_no AS question_sequence,
                       o.id AS option_id, o.option_text, o.option_code, o.score_value,
                       o.sequence_no AS option_sequence
                FROM survey_questions q LEFT JOIN survey_question_options o ON o.question_id = q.id
                WHERE q.survey_id = ? AND q.status = 'ACTIVE'
                ORDER BY q.sequence_no, o.sequence_no
                """)
                .param(surveyId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapQuestionOption)
                .toList();
    }

    /** 전체 결과 정보를 DB에서 조회합니다. */
    public List<PersonalityResult> findAllResults() {
        return jdbc.sql("""
                SELECT result_code, emoji, name, summary, character_key, hashtags,
                       strengths, weaknesses, compatible_types, travel_role
                FROM travel_personality_results ORDER BY result_code
                """)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapResult)
                .toList();
    }

    /** 결과 조건에 맞는 성향 설문 데이터를 DB에서 조회합니다. */
    public Optional<PersonalityResult> findResult(String code) {
        return jdbc.sql("""
                SELECT result_code, emoji, name, summary, character_key, hashtags,
                       strengths, weaknesses, compatible_types, travel_role
                FROM travel_personality_results WHERE result_code = ?
                """)
                .param(code)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapResult);
    }

    /** 변경 충돌을 막기 위해 활성 사용자 DB 행을 잠급니다. */
    public boolean lockActiveUser(long userId) {
        return jdbc.sql("""
                SELECT id FROM users WHERE id = ? AND status = 'ACTIVE' AND deleted_at IS NULL FOR UPDATE
                """)
                .param(userId)
                .query(Long.class)
                .optional()
                .isPresent();
    }

    /** 활성 문항 목록 조건에 맞는 성향 설문 데이터를 DB에서 조회합니다. */
    public List<ActiveQuestion> findActiveQuestions(long surveyId) {
        return jdbc.sql("""
                SELECT q.id AS question_id, q.axis_type, q.sequence_no AS question_sequence
                FROM surveys s JOIN survey_questions q ON q.survey_id = s.id AND q.status = 'ACTIVE'
                WHERE s.id = ? AND s.status = 'ACTIVE' ORDER BY q.sequence_no
                """)
                .param(surveyId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapActiveQuestion)
                .toList();
    }

    /** 선택지 목록 조건에 맞는 성향 설문 데이터를 DB에서 조회합니다. */
    public List<Option> findOptions(long surveyId) {
        return jdbc.sql("""
                SELECT o.id AS option_id, o.question_id, o.score_value,
                       o.sequence_no AS option_sequence, q.sequence_no AS question_sequence
                FROM survey_question_options o
                JOIN survey_questions q ON q.id = o.question_id AND q.status = 'ACTIVE'
                JOIN surveys s ON s.id = q.survey_id AND s.status = 'ACTIVE'
                WHERE s.id = ? ORDER BY q.sequence_no, o.sequence_no
                """)
                .param(surveyId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapOption)
                .toList();
    }

    /** 이전 응답 시도 정보를 DB에서 삭제합니다. */
    public void deletePreviousAttempt(Long tripId, long userId, long surveyId) {
        if (tripId == null) {
            jdbc.sql("DELETE FROM survey_attempts WHERE trip_id IS NULL AND user_id = ? AND survey_id = ?")
                    .params(
                            userId,
                            surveyId)
                    .update();
        } else {
            jdbc.sql("DELETE FROM survey_attempts WHERE trip_id = ? AND user_id = ? AND survey_id = ?")
                    .params(
                            tripId,
                            userId,
                            surveyId)
                    .update();
        }
    }

    /** 응답 시도 정보를 DB에 저장합니다. */
    public long createAttempt(
            long userId,
            long surveyId,
            Long tripId,
            int preparationScore,
            int placeScore,
            int energyScore,
            String preparationType,
            String placePreference,
            String travelStyle,
            String resultCode) {
        return keyHelper.insert("""
                INSERT INTO survey_attempts (user_id, survey_id, trip_id, status,
                    travel_prep_score, place_pref_score, travel_style_score,
                    preparation_type, place_preference, travel_style, result_code,
                    started_at, completed_at)
                VALUES (?, ?, ?, 'COMPLETED', ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                userId,
                surveyId,
                tripId,
                preparationScore,
                placeScore,
                energyScore,
                preparationType,
                placePreference,
                travelStyle,
                resultCode);
    }

    /** 응답 성향 설문 데이터를 DB에 저장합니다. */
    public void addResponse(long attemptId, long questionId, long optionId, int scoreValue) {
        jdbc.sql("""
                INSERT INTO question_responses (attempt_id, question_id, option_id, score_value)
                VALUES (?, ?, ?, ?)
                """)
                .params(
                        attemptId,
                        questionId,
                        optionId,
                        scoreValue)
                .update();
    }

    /** 응답 시도 정보를 DB에서 조회합니다. */
    public Optional<Attempt> findAttempt(long attemptId) {
        return jdbc.sql("""
                SELECT id, trip_id, result_code, travel_prep_score, place_pref_score, travel_style_score
                FROM survey_attempts WHERE id = ?
                """)
                .param(attemptId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::mapAttempt);
    }

    /** 성향 인원수 정보를 DB에서 조회합니다. */
    public List<ProfileCount> findProfileCounts(long tripId, long surveyId) {
        return jdbc.sql("""
                SELECT result_code, COUNT(*) AS response_count FROM survey_attempts
                WHERE trip_id = ? AND survey_id = ? AND status = 'COMPLETED'
                GROUP BY result_code ORDER BY response_count DESC, result_code
                """)
                .params(
                        tripId,
                        surveyId)
                .query()
                .listOfRows()
                .stream()
                .map(this::mapProfileCount)
                .toList();
    }

    /** 최근 응답 시도 식별자 정보를 DB에서 조회합니다. */
    public Optional<Long> findLatestAttemptId(long tripId, long surveyId) {
        return jdbc.sql("""
                SELECT id FROM survey_attempts WHERE trip_id = ? AND survey_id = ?
                AND status = 'COMPLETED' ORDER BY completed_at DESC LIMIT 1
                """)
                .params(
                        tripId,
                        surveyId)
                .query(Long.class)
                .optional();
    }

    /** 활성 문항에 대한 성향 설문 기능을 처리합니다. */
    public int activeQuestionCount(long surveyId) {
        return Math.toIntExact(jdbc.sql("""
                SELECT COUNT(*) FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
                WHERE s.id = ? AND s.status = 'ACTIVE' AND q.status = 'ACTIVE'
                """)
                .param(surveyId)
                .query(Long.class)
                .optional()
                .orElse(0L));
    }

    private Header mapHeader(Map<String, Object> row) {
        return new Header(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "name"),
                text(row, "description"),
                RowSupport.intValue(row, "version"),
                RowSupport.strValue(row, "status"));
    }

    private QuestionOption mapQuestionOption(Map<String, Object> row) {
        return new QuestionOption(
                RowSupport.longValue(row, "question_id"),
                RowSupport.strValue(row, "question_text"),
                RowSupport.strValue(row, "axis_type"),
                RowSupport.intValue(row, "question_sequence"),
                longValue(row, "option_id"),
                text(row, "option_text"),
                text(row, "option_code"),
                intValue(row, "score_value"),
                intValue(row, "option_sequence"));
    }

    private ActiveQuestion mapActiveQuestion(Map<String, Object> row) {
        return new ActiveQuestion(
                RowSupport.longValue(row, "question_id"),
                RowSupport.strValue(row, "axis_type"),
                RowSupport.intValue(row, "question_sequence"));
    }

    private Option mapOption(Map<String, Object> row) {
        return new Option(
                RowSupport.longValue(row, "option_id"),
                RowSupport.longValue(row, "question_id"),
                RowSupport.intValue(row, "score_value"),
                RowSupport.intValue(row, "option_sequence"),
                RowSupport.intValue(row, "question_sequence"));
    }

    private Attempt mapAttempt(Map<String, Object> row) {
        return new Attempt(
                RowSupport.longValue(row, "id"),
                longValue(row, "trip_id"),
                RowSupport.strValue(row, "result_code"),
                RowSupport.intValue(row, "travel_prep_score"),
                RowSupport.intValue(row, "place_pref_score"),
                RowSupport.intValue(row, "travel_style_score"));
    }

    private ProfileCount mapProfileCount(Map<String, Object> row) {
        return new ProfileCount(
                RowSupport.strValue(row, "result_code"),
                RowSupport.longValue(row, "response_count"));
    }

    private PersonalityResult mapResult(Map<String, Object> row) {
        return new PersonalityResult(
                RowSupport.strValue(row, "result_code"),
                text(row, "emoji"),
                RowSupport.strValue(row, "name"),
                RowSupport.strValue(row, "summary"),
                text(row, "character_key"),
                text(row, "hashtags"),
                text(row, "strengths"),
                text(row, "weaknesses"),
                text(row, "compatible_types"),
                text(row, "travel_role"));
    }

    private Object value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value != null ? value : row.get(key.toUpperCase(Locale.ROOT));
    }

    private String text(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null ? null : value.toString();
    }

    private Long longValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null
                ? null
                : value instanceof Number number
                        ? number.longValue()
                        : Long.valueOf(value.toString());
    }

    private Integer intValue(Map<String, Object> row, String key) {
        Object value = value(row, key);
        return value == null
                ? null
                : value instanceof Number number
                        ? number.intValue()
                        : Integer.valueOf(value.toString());
    }
}
