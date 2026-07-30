package com.gayadi.server.place

import com.gayadi.server.common.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service

@Service
class PlaceService(private val jdbc: JdbcClient) {

    fun list(category: String?): List<Map<String, Any>> =
        if (category.isNullOrBlank()) {
            jdbc.sql("SELECT * FROM places ORDER BY name").query().listOfRows()
        } else {
            jdbc.sql("SELECT * FROM places WHERE category = ? ORDER BY name")
                .param(category.uppercase()).query().listOfRows()
        }

    fun get(id: String): Map<String, Any> =
        jdbc.sql("SELECT * FROM places WHERE id = ?").param(id).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "장소를 찾을 수 없습니다.")
}
