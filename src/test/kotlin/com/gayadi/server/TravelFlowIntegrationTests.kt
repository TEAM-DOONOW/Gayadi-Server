package com.gayadi.server

import com.gayadi.server.auth.UserService
import com.gayadi.server.common.Location
import com.gayadi.server.event.EventService
import com.gayadi.server.route.RouteService
import com.gayadi.server.schedule.PlanService
import com.gayadi.server.survey.SurveyService
import com.gayadi.server.travel.DepartureMode
import com.gayadi.server.travel.TripService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.LocalDateTime

@SpringBootTest
class TravelFlowIntegrationTests {

    @Autowired lateinit var users: UserService
    @Autowired lateinit var trips: TripService
    @Autowired lateinit var surveys: SurveyService
    @Autowired lateinit var plans: PlanService
    @Autowired lateinit var routes: RouteService
    @Autowired lateinit var events: EventService

    @Test
    fun completeGroupTravelFlow() {
        val ownerId = id(users.create("여행장"))
        val memberUserId = id(users.create("친구"))
        val seoulStation = Location("서울역", 37.5547, 126.9706)

        val trip = trips.create(
            TripService.CreateTrip(
                ownerId,
                "서울 당일치기",
                DepartureMode.GROUP_MEETING,
                LocalDateTime.now().plusDays(1),
                LocalDateTime.now().plusDays(1).minusMinutes(40),
                seoulStation,
                Location("왕십리역", 37.5614, 127.0372),
                Location("왕십리역", 37.5614, 127.0372)
            )
        )
        val tripId = id(trip)
        val member = trips.addMember(
            tripId,
            TripService.AddMember(
                memberUserId,
                Location("강남역", 37.4979, 127.0276),
                Location("강남역", 37.4979, 127.0276)
            )
        )
        val memberId = id(member)

        surveys.respond(tripId, ownerId, mapOf("pace" to 2, "indoor" to 5, "food" to 3))
        surveys.respond(tripId, memberUserId, mapOf("pace" to 3, "indoor" to 4, "food" to 5))
        val plan = plans.generate(tripId)
        assertThat(number(plan, "revision_no")).isEqualTo(1)
        assertThat(plan["items"] as List<*>).hasSize(3)

        val memberRoute = routes.recommend(tripId, RouteService.RoutePhase.DEPARTURE, memberId)
        val groupRoute = routes.recommend(tripId, RouteService.RoutePhase.DEPARTURE, null)
        assertThat(memberRoute["scope"]).isEqualTo("MEMBER")
        assertThat(groupRoute["scope"]).isEqualTo("GROUP")

        trips.start(tripId)
        val proposal = events.observe(
            tripId,
            EventService.Observation(
                "20000000-0000-0000-0000-000000000001", "RAIN", "TEST", EventService.Severity.HIGH,
                mapOf("rainfallMm" to 20)
            )
        )
        val proposalId = id(proposal)
        events.decide(
            tripId, proposalId,
            EventService.Decision(true, "INDOOR_SHELTER", 1, ownerId)
        )
        assertThat(number(plans.get(tripId), "revision_no")).isEqualTo(2)

        val returnRoute = routes.recommend(tripId, RouteService.RoutePhase.RETURN, memberId)
        assertThat(returnRoute["phase"].toString()).isEqualTo("RETURN")
        assertThat(value(trips.complete(tripId), "status")).isEqualTo("COMPLETED")
    }

    private fun id(row: Map<String, Any>): String = value(row, "id").toString()

    private fun number(row: Map<String, Any>, key: String): Int = (value(row, key) as Number).toInt()

    private fun value(row: Map<String, Any>, key: String): Any =
        row[key] ?: row[key.uppercase()] ?: throw IllegalArgumentException("Missing key: $key")
}
