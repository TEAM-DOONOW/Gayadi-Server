package com.gayadi.server.schedule

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/trips/{tripId}/plan")
class PlanController(private val service: PlanService) {

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    fun generate(@PathVariable tripId: String): Map<String, Any> =
        service.generate(tripId)

    @GetMapping
    fun get(@PathVariable tripId: String): Map<String, Any> =
        service.get(tripId)
}
