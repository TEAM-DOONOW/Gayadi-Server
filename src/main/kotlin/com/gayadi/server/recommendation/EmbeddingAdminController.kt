package com.gayadi.server.recommendation

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/admin")
@ConditionalOnBean(PlaceEmbeddingService::class)
class EmbeddingAdminController(private val service: PlaceEmbeddingService) {

    @PostMapping("/embed-places")
    fun embedPlaces(): Map<String, Any> {
        val count = service.embedAllPlaces()
        return mapOf("status" to "completed", "embeddedCount" to count, "message" to "장소 임베딩이 완료되었습니다.")
    }
}
