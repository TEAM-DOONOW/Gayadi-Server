package com.gayadi.server.recommendation

import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/recommendations")
@ConditionalOnBean(RecommendationService::class)
class RecommendationController(private val service: RecommendationService) {

    @PostMapping("/places")
    fun recommendPlaces(@Valid @RequestBody request: PlaceRecommendationRequest): PlaceRecommendationResponse =
        service.recommendPlaces(request)

    data class PlaceRecommendationRequest(
        @field:NotBlank val profile: String,
        val latitude: Double,
        val longitude: Double,
        val keywords: List<String> = emptyList(),
        val limit: Int = 5
    )
}
