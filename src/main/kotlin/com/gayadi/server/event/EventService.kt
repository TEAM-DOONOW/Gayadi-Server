package com.gayadi.server.event

import com.gayadi.server.common.ApiException
import com.gayadi.server.common.JsonSupport
import com.gayadi.server.schedule.PlanService
import com.gayadi.server.travel.TripService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class EventService(
    private val jdbc: JdbcClient,
    private val trips: TripService,
    private val plans: PlanService,
    private val json: JsonSupport
) {

    companion object {
        private const val SHELTER_PLACE_ID = "20000000-0000-0000-0000-000000000004"
    }

    @Transactional
    fun observe(tripId: String, command: Observation): Map<String, Any> {
        trips.requireTrip(tripId)
        val plan = plans.get(tripId)
        val planId = PlanService.value(plan, "id").toString()
        val revision = (PlanService.value(plan, "revision_no") as Number).toInt()
        val eventId = UUID.randomUUID().toString()
        jdbc.sql(
            """
            INSERT INTO event_observations(id, place_id, event_type, source, observed_at, valid_to, severity, normalized_value)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).params(
            eventId, command.placeId, command.eventType, command.source, LocalDateTime.now(),
            LocalDateTime.now().plusHours(2), command.severity.name, json.write(command.values)
        ).update()
        if (command.severity == Severity.LOW) {
            return mapOf("eventId" to eventId, "impact" to false, "message" to "일정 변경이 필요하지 않습니다.")
        }
        val proposalId = UUID.randomUUID().toString()
        val option = mapOf(
            "key" to "INDOOR_SHELTER",
            "placeId" to SHELTER_PLACE_ID,
            "description" to "가까운 실내 대피 장소로 다음 일정을 변경합니다."
        )
        jdbc.sql(
            """
            INSERT INTO change_proposals(id, trip_id, plan_id, event_id, base_revision_no,
                                         reason, options, before_snapshot)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).params(
            proposalId, tripId, planId, eventId, revision,
            reason(command), json.write(listOf(option)), json.write(plan)
        ).update()
        return proposal(proposalId)
    }

    fun proposals(tripId: String): List<Map<String, Any>> {
        trips.requireTrip(tripId)
        return jdbc.sql("SELECT * FROM change_proposals WHERE trip_id = ? ORDER BY created_at DESC")
            .param(tripId).query().listOfRows()
    }

    @Transactional
    fun decide(tripId: String, proposalId: String, command: Decision): Map<String, Any> {
        trips.requireMember(tripId, command.decidedBy)
        val proposal = proposal(proposalId)
        if (tripId != PlanService.value(proposal, "trip_id").toString()) {
            throw ApiException(HttpStatus.NOT_FOUND, "해당 여행의 변경 제안이 아닙니다.")
        }
        if ("PENDING" != PlanService.value(proposal, "status").toString()) {
            throw ApiException(HttpStatus.CONFLICT, "이미 처리된 변경 제안입니다.")
        }
        val baseRevision = (PlanService.value(proposal, "base_revision_no") as Number).toInt()
        if (baseRevision != command.baseRevisionNo) {
            throw ApiException(HttpStatus.CONFLICT, "요청한 일정 버전이 변경 제안과 다릅니다.")
        }
        if (!command.approve) {
            jdbc.sql(
                """
                UPDATE change_proposals SET status = 'REJECTED', decided_by = ?, decided_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """
            ).params(command.decidedBy, proposalId).update()
            return proposal(proposalId)
        }
        val planId = PlanService.value(proposal, "plan_id").toString()
        val updated = jdbc.sql(
            """
            UPDATE trip_plans SET revision_no = revision_no + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND revision_no = ?
            """
        ).params(planId, baseRevision).update()
        if (updated == 0) {
            throw ApiException(HttpStatus.CONFLICT, "TRIP_REVISION_CONFLICT: 일정이 이미 변경되었습니다.")
        }
        jdbc.sql(
            """
            UPDATE trip_plan_items SET place_id = ?
            WHERE id = (SELECT id FROM trip_plan_items
                        WHERE plan_id = ? AND status = 'PLANNED'
                        ORDER BY sequence_no LIMIT 1)
            """
        ).params(SHELTER_PLACE_ID, planId).update()
        val after = plans.get(tripId)
        jdbc.sql(
            """
            UPDATE change_proposals SET status = 'APPROVED', selected_option = ?, after_snapshot = ?,
              decided_by = ?, decided_at = CURRENT_TIMESTAMP WHERE id = ?
            """
        ).params(
            json.write(mapOf("key" to command.selectedOptionKey)), json.write(after),
            command.decidedBy, proposalId
        ).update()
        return proposal(proposalId)
    }

    private fun proposal(id: String): Map<String, Any> {
        val row = jdbc.sql("SELECT * FROM change_proposals WHERE id = ?")
            .param(id).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "변경 제안을 찾을 수 없습니다.")
        return LinkedHashMap(row)
    }

    private fun reason(command: Observation): String =
        "${command.eventType} 이벤트가 ${command.severity} 단계로 관측되었습니다."

    enum class Severity { LOW, MEDIUM, HIGH, CRITICAL }

    data class Observation(
        val placeId: String,
        val eventType: String,
        val source: String,
        val severity: Severity,
        val values: Map<String, Any>
    )

    data class Decision(
        val approve: Boolean,
        val selectedOptionKey: String?,
        val baseRevisionNo: Int,
        val decidedBy: String
    )
}
