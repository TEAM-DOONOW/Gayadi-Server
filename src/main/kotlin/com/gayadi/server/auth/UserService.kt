package com.gayadi.server.auth

import com.gayadi.server.common.ApiException
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(private val jdbc: JdbcClient) {

    fun create(nickname: String): Map<String, Any> {
        val id = UUID.randomUUID().toString()
        jdbc.sql("INSERT INTO users(id, nickname, oauth_provider, oauth_subject) VALUES (?, ?, 'LOCAL', ?)")
            .params(id, nickname, "local:$id")
            .update()
        return get(id)
    }

    fun get(id: String): Map<String, Any> =
        jdbc.sql("SELECT id, nickname, oauth_provider, status, created_at FROM users WHERE id = ?")
            .param(id).query().listOfRows().firstOrNull()
            ?: throw ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")

    fun requireExists(id: String) {
        val count = jdbc.sql("SELECT COUNT(*) FROM users WHERE id = ?")
            .param(id).query(Int::class.java).single()
        if (count == 0) throw ApiException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.")
    }
}
