package com.gayadi.server.survey

import com.gayadi.server.common.ApiException
import com.gayadi.server.common.JsonSupport
import com.gayadi.server.travel.TripService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class SurveyService(
    private val jdbc: JdbcClient,
    private val trips: TripService,
    private val json: JsonSupport
) {

    companion object {
        private const val PERSONALITY_SURVEY_ID = "10000000-0000-0000-0000-000000000001"
    }

    fun personalitySurvey(): Map<String, Any> =
        jdbc.sql("SELECT id, survey_type, version, questions, status FROM surveys WHERE id = ?")
            .param(PERSONALITY_SURVEY_ID).query().singleRow()

    @Transactional
    fun respond(tripId: String, userId: String, answers: Map<String, Int>): Map<String, Any> {
        trips.requireMember(tripId, userId)
        validateAnswers(answers)
        val resultCode = calculateResult(answers)
        val id = UUID.randomUUID().toString()
        val resultData = mapOf(
            "profile" to resultCode,
            "scoreTotal" to answers.values.sum()
        )
        jdbc.sql("DELETE FROM survey_responses WHERE trip_id = ? AND survey_id = ? AND user_id = ?")
            .params(tripId, PERSONALITY_SURVEY_ID, userId).update()
        jdbc.sql(
            """
            INSERT INTO survey_responses(id, survey_id, user_id, trip_id, answers, result_code, result_data)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """
        ).params(id, PERSONALITY_SURVEY_ID, userId, tripId, json.write(answers), resultCode, json.write(resultData))
            .update()
        return response(id)
    }

    fun groupProfile(tripId: String): Map<String, Any> {
        trips.requireTrip(tripId)
        val profiles = jdbc.sql("SELECT result_code FROM survey_responses WHERE trip_id = ?")
            .param(tripId).query(String::class.java).list()
        if (profiles.isEmpty()) {
            throw ApiException(HttpStatus.CONFLICT, "일정 생성 전에 한 명 이상 성향 설문을 제출해야 합니다.")
        }
        val counts = linkedMapOf<String, Long>()
        profiles.forEach { profile -> counts.merge(profile, 1L) { a, b -> a + b } }
        val dominant = counts.entries.maxByOrNull { it.value }?.key
            ?: throw ApiException(HttpStatus.CONFLICT, "성향 분석에 실패했습니다.")
        return mapOf("dominantProfile" to dominant, "responseCount" to profiles.size, "distribution" to counts)
    }

    fun latestResponseId(tripId: String): String =
        jdbc.sql("SELECT id FROM survey_responses WHERE trip_id = ? ORDER BY created_at DESC LIMIT 1")
            .param(tripId).query(String::class.java).optional()
            .orElseThrow { ApiException(HttpStatus.CONFLICT, "성향 설문 응답이 필요합니다.") }

    private fun response(id: String): Map<String, Any> =
        jdbc.sql("SELECT * FROM survey_responses WHERE id = ?").param(id).query().singleRow()

    private fun validateAnswers(answers: Map<String, Int>) {
        if (!answers.keys.containsAll(listOf("pace", "indoor", "food"))) {
            throw ApiException(HttpStatus.BAD_REQUEST, "pace, indoor, food 답변이 모두 필요합니다.")
        }
        if (answers.values.any { it < 1 || it > 5 }) {
            throw ApiException(HttpStatus.BAD_REQUEST, "설문 점수는 1부터 5 사이여야 합니다.")
        }
    }

    private fun calculateResult(answers: Map<String, Int>): String = when {
        answers.getValue("indoor") >= 4 -> "INDOOR"
        answers.getValue("pace") >= 4 -> "ACTIVE"
        answers.getValue("food") >= 4 -> "FOODIE"
        else -> "BALANCED"
    }
}
