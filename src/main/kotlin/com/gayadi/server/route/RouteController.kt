package com.gayadi.server.route

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trips/{tripId}/routes")
class RouteController(private val service: RouteService) {

    @PostMapping("/recommend")
    fun recommend(
        @PathVariable tripId: String,
        @RequestParam phase: RouteService.RoutePhase,
        @RequestParam(required = false) memberId: String?
    ): Map<String, Any> =
        service.recommend(tripId, phase, memberId)
}
