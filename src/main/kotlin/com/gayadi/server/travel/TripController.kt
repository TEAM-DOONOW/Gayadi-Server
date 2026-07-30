package com.gayadi.server.travel

import com.gayadi.server.common.Location
import jakarta.validation.Valid
import jakarta.validation.constraints.Future
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/v1/trips")
class TripController(private val service: TripService) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateTripRequest): Map<String, Any> =
        service.create(
            TripService.CreateTrip(
                request.ownerId, request.title, request.departureMode,
                request.departureAt, request.meetingAt, request.meetingLocation,
                request.ownerDeparture, request.ownerReturn
            )
        )

    @GetMapping("/{tripId}")
    fun get(@PathVariable tripId: String): Map<String, Any> =
        service.get(tripId)

    @GetMapping("/{tripId}/members")
    fun members(@PathVariable tripId: String): List<Map<String, Any>> =
        service.members(tripId)

    @PostMapping("/{tripId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMember(
        @PathVariable tripId: String,
        @Valid @RequestBody request: AddMemberRequest
    ): Map<String, Any> =
        service.addMember(
            tripId,
            TripService.AddMember(request.userId, request.departureLocation, request.returnDestination)
        )

    @PostMapping("/{tripId}/start")
    fun start(@PathVariable tripId: String): Map<String, Any> =
        service.start(tripId)

    @PostMapping("/{tripId}/complete")
    fun complete(@PathVariable tripId: String): Map<String, Any> =
        service.complete(tripId)

    data class CreateTripRequest(
        @field:NotBlank val ownerId: String,
        @field:NotBlank @field:Size(max = 120) val title: String,
        @field:NotNull val departureMode: DepartureMode,
        @field:NotNull @field:Future val departureAt: LocalDateTime,
        val meetingAt: LocalDateTime?,
        @field:Valid val meetingLocation: Location?,
        @field:NotNull @field:Valid val ownerDeparture: Location,
        @field:NotNull @field:Valid val ownerReturn: Location
    )

    data class AddMemberRequest(
        @field:NotBlank val userId: String,
        @field:NotNull @field:Valid val departureLocation: Location,
        @field:NotNull @field:Valid val returnDestination: Location
    )
}
