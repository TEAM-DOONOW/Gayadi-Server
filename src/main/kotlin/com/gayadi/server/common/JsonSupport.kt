package com.gayadi.server.common

import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper

@Component
class JsonSupport(private val objectMapper: ObjectMapper) {

    fun write(value: Any): String = try {
        objectMapper.writeValueAsString(value)
    } catch (exception: JacksonException) {
        throw IllegalArgumentException("JSON 변환에 실패했습니다.", exception)
    }
}
