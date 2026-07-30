package com.gayadi.server.schedule

import com.gayadi.server.common.ApiException
import com.gayadi.server.common.JsonSupport
import com.gayadi.server.survey.SurveyService
import com.gayadi.server.travel.TripService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID

@Service
class PlanService(
    private val jdbc: JdbcClient,
    private val trips: TripService,
    private val surveys: SurveyService,
    private val json: JsonSupport
) {

    companion object {
        fun value(row: Map<String, Any>, key: String): Any =
            row[key] ?: row[key.uppercase()] ?: throw IllegalArgumentException("Missing key: $key")
    }

    @Transactional
    fun generate(tripId: String): Map<String, Any> {
        val trip = trips.requireTrip(tripId)
        val status = value(trip, "status").toString()
        if (status !in listOf("DRAFT", "READY")) {
            throw ApiException(HttpStatus.CONFLICT, "여행 시작 전 일정만 다시 생성할 수 있습니다.")
        }
        val profile = surveys.groupProfile(tripId)
        val responseId = surveys.latestResponseId(tripId)
        var planId = jdbc.sql("SELECT id FROM trip_plans WHERE trip_id = ?")
            .param(tripId).query(String::class.java).optional().orElse(null)
        if (planId == null) {
            planId = UUID.randomUUID().toString()
            jdbc.sql(
                """
                INSERT INTO trip_plans(id, trip_id, survey_response_id, revision_no, preference_snapshot)
                VALUES (?, ?, ?, 1, ?)
                """
            ).params(planId, tripId, responseId, json.write(profile)).update()
        } else {
            jdbc.sql("DELETE FROM trip_plan_items WHERE plan_id = ?").param(planId).update()
            jdbc.sql(
                """
                UPDATE trip_plans SET survey_response_id = ?, revision_no = revision_no + 1,
                preference_snapshot = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?
                """
            ).params(responseId, json.write(profile), planId).update()
        }
        val dominant = profile["dominantProfile"].toString()
        val placeIds = orderedPlaces(dominant)
        val start = toLocalDateTime(value(trip, "departure_at"))
        placeIds.forEachIndexed { index, placeId ->
            val itemStart = start.plusMinutes(index * 120L)
            jdbc.sql(
                """
                INSERT INTO trip_plan_items(id, plan_id, place_id, sequence_no, planned_start, planned_end)
                VALUES (?, ?, ?, ?, ?, ?)
                """
            ).params(UUID.randomUUID().toString(), planId, placeId, index + 1, itemStart, itemStart.plusMinutes(90))
                .update()
        }
        trips.markReady(tripId)
        return get(tripId)
    }

    fun get(tripId: String): Map<String, Any> {
        trips.requireTrip(tripId)
        val row = jdbc.sql("SELECT * FROM trip_plans WHERE trip_id = ?")
            .param(tripId).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "생성된 일정이 없습니다.")
        val plan = LinkedHashMap(row)
        val planId = value(plan, "id").toString()
        val items = jdbc.sql(
            """
            SELECT i.id, i.sequence_no, i.planned_start, i.planned_end, i.status,
                   p.id AS place_id, p.name AS place_name, p.category, p.address,
                   p.latitude, p.longitude
            FROM trip_plan_items i JOIN places p ON p.id = i.place_id
            WHERE i.plan_id = ? ORDER BY i.sequence_no
            """
        ).param(planId).query().listOfRows()
        plan["items"] = items
        return plan
    }

    fun firstPlace(tripId: String): Map<String, Any> = boundaryPlace(tripId, "ASC")

    fun lastPlace(tripId: String): Map<String, Any> = boundaryPlace(tripId, "DESC")

    private fun boundaryPlace(tripId: String, order: String): Map<String, Any> {
        val sql = """
            SELECT p.* FROM trip_plans tp
            JOIN trip_plan_items i ON i.plan_id = tp.id
            JOIN places p ON p.id = i.place_id
            WHERE tp.trip_id = ? ORDER BY i.sequence_no $order LIMIT 1
        """.trimIndent()
        return jdbc.sql(sql).param(tripId).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.CONFLICT, "경로 계산 전에 일정이 필요합니다.")
    }

    private fun orderedPlaces(profile: String): List<String> {
        val ids = mutableListOf<String>()
        when (profile) {
            "INDOOR" -> ids.add("20000000-0000-0000-0000-000000000002")
            "FOODIE" -> ids.add("20000000-0000-0000-0000-000000000003")
            else -> ids.add("20000000-0000-0000-0000-000000000001")
        }
        for (candidate in listOf(
            "20000000-0000-0000-0000-000000000003",
            "20000000-0000-0000-0000-000000000002",
            "20000000-0000-0000-0000-000000000001"
        )) {
            if (candidate !in ids) ids.add(candidate)
        }
        return ids.subList(0, 3)
    }

    private fun toLocalDateTime(value: Any): LocalDateTime = when (value) {
        is LocalDateTime -> value
        is Timestamp -> value.toLocalDateTime()
        else -> LocalDateTime.parse(value.toString().replace(' ', 'T'))
    }
}
