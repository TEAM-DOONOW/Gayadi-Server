package com.gayadi.server.survey

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class SurveyController(private val service: SurveyService) {

    @GetMapping("/surveys/personality")
    fun personalitySurvey(): Map<String, Any> =
        service.personalitySurvey()

    @PostMapping("/trips/{tripId}/survey-responses")
    @ResponseStatus(HttpStatus.CREATED)
    fun respond(
        @PathVariable tripId: String,
        @Valid @RequestBody request: ResponseRequest
    ): Map<String, Any> =
        service.respond(tripId, request.userId, request.answers)

    @GetMapping("/trips/{tripId}/personality-profile")
    fun groupProfile(@PathVariable tripId: String): Map<String, Any> =
        service.groupProfile(tripId)

    data class ResponseRequest(
        @field:NotBlank val userId: String,
        @field:NotEmpty val answers: Map<String, Int>
    )
}
