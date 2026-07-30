package com.gayadi.server.place

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/places")
class PlaceController(private val service: PlaceService) {

    @GetMapping
    fun list(@RequestParam(required = false) category: String?): List<Map<String, Any>> =
        service.list(category)

    @GetMapping("/{placeId}")
    fun get(@PathVariable placeId: String): Map<String, Any> =
        service.get(placeId)
}
