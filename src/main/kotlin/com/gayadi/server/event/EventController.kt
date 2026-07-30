package com.gayadi.server.event

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
class EventController(private val service: EventService) {

    @PostMapping("/event-observations")
    @ResponseStatus(HttpStatus.CREATED)
    fun observe(
        @PathVariable tripId: String,
        @Valid @RequestBody request: ObservationRequest
    ): Map<String, Any> =
        service.observe(
            tripId,
            EventService.Observation(request.placeId, request.eventType, request.source, request.severity, request.values)
        )

    @GetMapping("/change-proposals")
    fun proposals(@PathVariable tripId: String): List<Map<String, Any>> =
        service.proposals(tripId)

    @PostMapping("/change-proposals/{proposalId}/decision")
    fun decide(
        @PathVariable tripId: String,
        @PathVariable proposalId: String,
        @Valid @RequestBody request: DecisionRequest
    ): Map<String, Any> =
        service.decide(
            tripId, proposalId,
            EventService.Decision(request.approve, request.selectedOptionKey, request.baseRevisionNo, request.decidedBy)
        )

    data class ObservationRequest(
        @field:NotBlank val placeId: String,
        @field:NotBlank val eventType: String,
        @field:NotBlank val source: String,
        @field:NotNull val severity: EventService.Severity,
        @field:NotEmpty val values: Map<String, Any>
    )

    data class DecisionRequest(
        val approve: Boolean,
        val selectedOptionKey: String?,
        @field:Positive val baseRevisionNo: Int,
        @field:NotBlank val decidedBy: String
    )
}
