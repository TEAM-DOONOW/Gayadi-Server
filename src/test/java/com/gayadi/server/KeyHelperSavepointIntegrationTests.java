package com.gayadi.server;

import com.gayadi.server.common.KeyHelper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class KeyHelperSavepointIntegrationTests {

    @Autowired KeyHelper keyHelper;
    @Autowired JdbcClient jdbc;

    @Test
    void uniqueViolationRollsBackToSavepointAndKeepsTransactionUsable() {
        String suffix = Long.toUnsignedString(System.nanoTime());
        String firstEmail = "savepoint-" + suffix + "@example.com";
        String secondEmail = "savepoint-next-" + suffix + "@example.com";

        long firstId = keyHelper.insert("""
                INSERT INTO users (nickname, email) VALUES (?, ?)
                """, "저장점첫째", firstEmail);

        OptionalLong duplicate = keyHelper.insertOrEmptyOnUniqueViolation("""
                INSERT INTO users (nickname, email) VALUES (?, ?)
                """, "저장점중복", firstEmail);

        assertThat(duplicate).isEmpty();
        assertThat(jdbc.sql("SELECT id FROM users WHERE email = ?")
                .param(firstEmail)
                .query(Long.class)
                .single()).isEqualTo(firstId);

        long secondId = keyHelper.insertOrEmptyOnUniqueViolation("""
                INSERT INTO users (nickname, email) VALUES (?, ?)
                """, "저장점둘째", secondEmail).orElseThrow();

        assertThat(secondId).isNotEqualTo(firstId);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM users WHERE email IN (?, ?)")
                .params(firstEmail, secondEmail)
                .query(Long.class)
                .single()).isEqualTo(2L);
    }
}
