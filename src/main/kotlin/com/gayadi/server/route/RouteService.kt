package com.gayadi.server.route

import com.gayadi.server.common.ApiException
import com.gayadi.server.common.JsonSupport
import com.gayadi.server.common.Location
import com.gayadi.server.schedule.PlanService
import com.gayadi.server.travel.DepartureMode
import com.gayadi.server.travel.TripService
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.time.LocalDateTime
import java.util.UUID

@Service
class RouteService(
    private val jdbc: JdbcClient,
    private val trips: TripService,
    private val plans: PlanService,
    private val provider: RouteProvider,
    private val objectMapper: ObjectMapper,
    private val json: JsonSupport
) {

    fun recommend(tripId: String, phase: RoutePhase, memberId: String?): Map<String, Any> {
        val trip = trips.requireTrip(tripId)
        val member = memberId?.let { member(tripId, it) }
        val context = when (phase) {
            RoutePhase.DEPARTURE -> departureContext(trip, member)
            RoutePhase.RETURN -> returnContext(tripId, member)
            RoutePhase.BETWEEN_PLACES -> throw ApiException(
                HttpStatus.NOT_IMPLEMENTED, "장소 간 경로는 일정 변경 흐름에서 계산됩니다."
            )
        }
        val estimate = provider.estimate(context.origin, context.destination, phase.name)
        val id = UUID.randomUUID().toString()
        val routeData = mapOf("provider" to "LOCAL_STUB", "summary" to estimate.summary)
        jdbc.sql(
            """
            INSERT INTO trip_routes(id, trip_id, member_id, scope, phase, origin, destination,
                                    duration_minutes, transfer_count, fare, route_data, valid_until)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
        ).params(
            id, tripId, memberId, context.scope, phase.name, json.write(context.origin),
            json.write(context.destination), estimate.durationMinutes, estimate.transferCount,
            estimate.fare, json.write(routeData), LocalDateTime.now().plusMinutes(10)
        ).update()
        return linkedMapOf(
            "id" to id,
            "tripId" to tripId,
            "memberId" to memberId,
            "scope" to context.scope,
            "phase" to phase,
            "origin" to context.origin,
            "destination" to context.destination,
            "durationMinutes" to estimate.durationMinutes,
            "transferCount" to estimate.transferCount,
            "fare" to estimate.fare,
            "validUntil" to LocalDateTime.now().plusMinutes(10),
            "provider" to "LOCAL_STUB"
        )
    }

    private fun departureContext(trip: Map<String, Any>, member: Map<String, Any>?): RouteContext {
        val mode = DepartureMode.valueOf(PlanService.value(trip, "departure_mode").toString())
        val firstPlace = placeLocation(plans.firstPlace(PlanService.value(trip, "id").toString()))
        if (mode == DepartureMode.GROUP_MEETING && member == null) {
            return RouteContext(readLocation(PlanService.value(trip, "meeting_location")), firstPlace, "GROUP")
        }
        if (member == null) {
            throw ApiException(HttpStatus.BAD_REQUEST, "개별 출발 경로에는 memberId가 필요합니다.")
        }
        val origin = readLocation(PlanService.value(member, "departure_location"))
        val destination = if (mode == DepartureMode.GROUP_MEETING) {
            readLocation(PlanService.value(trip, "meeting_location"))
        } else {
            firstPlace
        }
        return RouteContext(origin, destination, "MEMBER")
    }

    private fun returnContext(tripId: String, member: Map<String, Any>?): RouteContext {
        if (member == null) throw ApiException(HttpStatus.BAD_REQUEST, "귀가 경로에는 memberId가 필요합니다.")
        return RouteContext(
            placeLocation(plans.lastPlace(tripId)),
            readLocation(PlanService.value(member, "return_destination")),
            "MEMBER"
        )
    }

    private fun member(tripId: String, memberId: String): Map<String, Any> =
        jdbc.sql("SELECT * FROM trip_members WHERE trip_id = ? AND id = ?")
            .params(tripId, memberId).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "여행 멤버를 찾을 수 없습니다.")

    private fun placeLocation(place: Map<String, Any>): Location =
        Location(
            PlanService.value(place, "name").toString(),
            (PlanService.value(place, "latitude") as Number).toDouble(),
            (PlanService.value(place, "longitude") as Number).toDouble()
        )

    private fun readLocation(raw: Any): Location = try {
        objectMapper.readValue(raw.toString(), Location::class.java)
    } catch (exception: JacksonException) {
        throw ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "저장된 위치 정보를 읽을 수 없습니다.")
    }

    enum class RoutePhase { DEPARTURE, BETWEEN_PLACES, RETURN }

    private data class RouteContext(val origin: Location, val destination: Location, val scope: String)
}
