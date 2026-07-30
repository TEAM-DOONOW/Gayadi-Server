package com.gayadi.server.common

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.time.Instant
import java.util.UUID

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(ApiException::class)
    fun handleApi(exception: ApiException, request: HttpServletRequest): ResponseEntity<Map<String, Any>> =
        error(exception.status, exception.message ?: "오류가 발생했습니다.", request.requestURI)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(
        exception: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<Map<String, Any>> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()
            ?.let { "${it.field}: ${it.defaultMessage}" }
            ?: "요청값이 올바르지 않습니다."
        return error(HttpStatus.BAD_REQUEST, message, request.requestURI)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(exception: Exception, request: HttpServletRequest): ResponseEntity<Map<String, Any>> {
        log.error("Unhandled request error: {} {}", request.method, request.requestURI, exception)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", request.requestURI)
    }

    private fun error(status: HttpStatus, message: String, path: String): ResponseEntity<Map<String, Any>> {
        val body = linkedMapOf<String, Any>(
            "timestamp" to Instant.now(),
            "status" to status.value(),
            "code" to status.name,
            "message" to message,
            "path" to path,
            "traceId" to UUID.randomUUID().toString(),
            "details" to emptyMap<String, Any>()
        )
        return ResponseEntity.status(status).body(body)
    }
}
