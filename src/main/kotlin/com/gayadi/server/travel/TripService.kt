package com.gayadi.server.travel

import com.gayadi.server.auth.UserService
import com.gayadi.server.common.ApiException
import com.gayadi.server.common.JsonSupport
import com.gayadi.server.common.Location
import org.springframework.dao.DuplicateKeyException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class TripService(
    private val jdbc: JdbcClient,
    private val users: UserService,
    private val json: JsonSupport
) {

    @Transactional
    fun create(command: CreateTrip): Map<String, Any> {
        users.requireExists(command.ownerId)
        validateDeparture(command.departureMode, command.meetingAt, command.meetingLocation)
        val tripId = UUID.randomUUID().toString()
        jdbc.sql(
            """
            INSERT INTO trips(id, owner_id, title, departure_mode, departure_at, meeting_at, meeting_location)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """
        ).params(
            tripId, command.ownerId, command.title, command.departureMode.name,
            command.departureAt, command.meetingAt, nullableJson(command.meetingLocation)
        ).update()
        addMemberInternal(tripId, command.ownerId, "OWNER", command.ownerDeparture, command.ownerReturn)
        return get(tripId)
    }

    @Transactional
    fun addMember(tripId: String, command: AddMember): Map<String, Any> {
        requireTrip(tripId)
        users.requireExists(command.userId)
        addMemberInternal(tripId, command.userId, "MEMBER", command.departureLocation, command.returnDestination)
        return memberByUser(tripId, command.userId)
    }

    fun get(tripId: String): Map<String, Any> {
        val row = jdbc.sql(
            """
            SELECT id, owner_id, title, departure_mode, departure_at, meeting_at, meeting_location, status, created_at
            FROM trips WHERE id = ?
            """
        ).param(tripId).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다.")
        val trip = LinkedHashMap(row)
        trip["members"] = members(tripId)
        return trip
    }

    fun members(tripId: String): List<Map<String, Any>> {
        requireTrip(tripId)
        return jdbc.sql(
            """
            SELECT tm.id, tm.user_id, u.nickname, tm.role, tm.participation_status,
                   tm.departure_location, tm.return_destination, tm.route_preferences
            FROM trip_members tm JOIN users u ON u.id = tm.user_id
            WHERE tm.trip_id = ? ORDER BY tm.created_at
            """
        ).param(tripId).query().listOfRows()
    }

    @Transactional
    fun start(tripId: String): Map<String, Any> {
        transition(tripId, TripStatus.READY, TripStatus.IN_PROGRESS)
        return get(tripId)
    }

    @Transactional
    fun complete(tripId: String): Map<String, Any> {
        val trip = get(tripId)
        val current = TripStatus.valueOf(rowValue(trip, "status").toString())
        if (current != TripStatus.IN_PROGRESS && current != TripStatus.RETURNING) {
            throw ApiException(HttpStatus.CONFLICT, "진행 중인 여행만 완료할 수 있습니다.")
        }
        jdbc.sql("UPDATE trips SET status = 'COMPLETED' WHERE id = ?").param(tripId).update()
        return get(tripId)
    }

    fun markReady(tripId: String) {
        jdbc.sql("UPDATE trips SET status = 'READY' WHERE id = ? AND status IN ('DRAFT', 'READY')")
            .param(tripId).update()
    }

    fun requireMember(tripId: String, userId: String) {
        val count = jdbc.sql("SELECT COUNT(*) FROM trip_members WHERE trip_id = ? AND user_id = ?")
            .params(tripId, userId).query(Int::class.java).single()
        if (count == 0) throw ApiException(HttpStatus.FORBIDDEN, "여행 멤버만 수행할 수 있습니다.")
    }

    fun requireTrip(tripId: String): Map<String, Any> =
        jdbc.sql("SELECT * FROM trips WHERE id = ?").param(tripId).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "여행을 찾을 수 없습니다.")

    private fun validateDeparture(mode: DepartureMode, meetingAt: LocalDateTime?, meetingLocation: Location?) {
        if (mode == DepartureMode.GROUP_MEETING && (meetingAt == null || meetingLocation == null)) {
            throw ApiException(HttpStatus.BAD_REQUEST, "모여서 출발하는 여행은 집결 시각과 장소가 필요합니다.")
        }
    }

    private fun transition(tripId: String, expected: TripStatus, target: TripStatus) {
        val updated = jdbc.sql("UPDATE trips SET status = ? WHERE id = ? AND status = ?")
            .params(target.name, tripId, expected.name).update()
        if (updated == 0) throw ApiException(HttpStatus.CONFLICT, "$expected 상태에서만 전환할 수 있습니다.")
    }

    private fun addMemberInternal(
        tripId: String, userId: String, role: String, departure: Location, returning: Location
    ) {
        val id = UUID.randomUUID().toString()
        try {
            jdbc.sql(
                """
                INSERT INTO trip_members(id, trip_id, user_id, role, departure_location, return_destination, route_preferences)
                VALUES (?, ?, ?, ?, ?, ?, '{}')
                """
            ).params(id, tripId, userId, role, json.write(departure), json.write(returning)).update()
        } catch (exception: DuplicateKeyException) {
            throw ApiException(HttpStatus.CONFLICT, "이미 참여 중인 멤버입니다.")
        }
    }

    private fun memberByUser(tripId: String, userId: String): Map<String, Any> =
        jdbc.sql("SELECT * FROM trip_members WHERE trip_id = ? AND user_id = ?")
            .params(tripId, userId).query().singleRow()

    private fun nullableJson(value: Any?): String? = value?.let { json.write(it) }

    private fun rowValue(row: Map<String, Any>, key: String): Any =
        row[key] ?: row[key.uppercase()] ?: throw IllegalArgumentException("Missing key: $key")

    data class CreateTrip(
        val ownerId: String,
        val title: String,
        val departureMode: DepartureMode,
        val departureAt: LocalDateTime,
        val meetingAt: LocalDateTime?,
        val meetingLocation: Location?,
        val ownerDeparture: Location,
        val ownerReturn: Location
    )

    data class AddMember(
        val userId: String,
        val departureLocation: Location,
        val returnDestination: Location
    )
}
