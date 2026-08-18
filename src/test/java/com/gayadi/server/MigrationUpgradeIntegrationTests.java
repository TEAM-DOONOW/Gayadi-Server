package com.gayadi.server;

import org.assertj.core.api.Assertions;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

class MigrationUpgradeIntegrationTests {

    @Test
    void repairsLegacyRowsBeforeAddingConstraints() throws Exception {
        String url = "jdbc:h2:mem:upgrade_" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        // Flyway가 내부 연결을 닫는 동안에도 H2 메모리 DB를 유지한다.
        try (Connection keepAlive = DriverManager.getConnection(url, "sa", "")) {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(keepAlive, true);
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("7"))
                .load()
                .migrate();

        long tripId;
        try (Statement sql = keepAlive.createStatement()) {
            long userId = generatedId(sql,
                    "INSERT INTO users (nickname) VALUES ('이관 사용자')");
            long duplicateRegionId = generatedId(sql,
                    "INSERT INTO regions (name) VALUES ('서울')");
            tripId = generatedId(sql, """
                    INSERT INTO trips
                        (owner_id, title, start_date, end_date, departure_mode,
                         region_id, status, max_members)
                    VALUES (%d, '이관 여행', DATE '2026-08-20', DATE '2026-08-10',
                            'SEPARATE', %d, 'PLANNING', 0)
                    """.formatted(userId, duplicateRegionId));
            sql.executeUpdate("""
                    INSERT INTO trip_participants (trip_id, user_id, role, status)
                    VALUES (%d, %d, 'OWNER', 'JOINED')
                    """.formatted(tripId, userId));

            long firstPlan = generatedId(sql, """
                    INSERT INTO travel_plans
                        (trip_id, plan_date, day_number, title, source_type, status, created_by)
                    VALUES (%d, DATE '2026-08-09', 0, '중복 일정 1', 'MANUAL', 'DRAFT', %d)
                    """.formatted(tripId, userId));
            long secondPlan = generatedId(sql, """
                    INSERT INTO travel_plans
                        (trip_id, plan_date, day_number, title, source_type, status, created_by)
                    VALUES (%d, DATE '2026-08-09', 0, '중복 일정 2', 'MANUAL', 'DRAFT', %d)
                    """.formatted(tripId, userId));
            sql.executeUpdate("""
                    INSERT INTO travel_plan_items
                        (plan_id, place_id, item_type, title, sequence_no, status)
                    VALUES (%d, 1, 'PLACE', '첫 일정', -1, 'PLANNED'),
                           (%d, 2, 'PLACE', '둘째 일정', 1, 'PLANNED')
                    """.formatted(firstPlan, secondPlan));

            long attemptId = generatedId(sql, """
                    INSERT INTO survey_attempts
                        (user_id, survey_id, trip_id, status, result_code, started_at, completed_at)
                    VALUES (%d, 2, %d, 'COMPLETED', 'PNA', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """.formatted(userId, tripId));
            sql.executeUpdate("""
                    INSERT INTO question_responses
                        (attempt_id, question_id, option_id, score_value)
                    VALUES (%d, 10, 20, 1),
                           (%d, 10, 21, -1),
                           (%d, 10, 22, 1)
                    """.formatted(attemptId, attemptId, attemptId));
            sql.executeUpdate("UPDATE survey_questions SET sequence_no = 1 WHERE id = 11");

            sql.executeUpdate("""
                    INSERT INTO travel_invitations
                        (trip_id, inviter_id, invitee_user_id, invite_code, status, expires_at)
                    VALUES (%d, %d, %d, 'OLD-CODE', 'PENDING', TIMESTAMP '2099-01-01 00:00:00'),
                           (%d, %d, %d, 'OLD-CODE', 'PENDING', TIMESTAMP '2099-01-01 00:00:00')
                    """.formatted(tripId, userId, userId, tripId, userId, userId));
            sql.executeUpdate("UPDATE places SET latitude = 91, longitude = -181 WHERE id = 1");
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("13"))
                .load()
                .migrate();
        try (Statement sql = keepAlive.createStatement()) {
            sql.executeUpdate("UPDATE trips SET invite_code = 'abc-12' WHERE id = " + tripId);
        }

        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Statement sql = keepAlive.createStatement()) {
            Assertions.assertThat(single(sql,
                    "SELECT CAST(start_date AS VARCHAR) FROM trips WHERE id = " + tripId))
                    .startsWith("2026-08-10");
            Assertions.assertThat(single(sql,
                    "SELECT CAST(end_date AS VARCHAR) FROM trips WHERE id = " + tripId))
                    .startsWith("2026-08-20");
            Assertions.assertThat(number(sql,
                    "SELECT max_members FROM trips WHERE id = " + tripId)).isEqualTo(1);
            Assertions.assertThat(number(sql,
                    "SELECT region_id FROM trips WHERE id = " + tripId)).isEqualTo(1);
            Assertions.assertThat(number(sql,
                    "SELECT COUNT(*) FROM regions WHERE name = '서울'")).isEqualTo(1);
            Assertions.assertThat(number(sql,
                    "SELECT COUNT(*) FROM travel_plans WHERE trip_id = " + tripId)).isEqualTo(1);
            Assertions.assertThat(number(sql, """
                    SELECT COUNT(*) FROM travel_plan_items i
                    JOIN travel_plans p ON p.id = i.plan_id
                    WHERE p.trip_id = %d
                    """.formatted(tripId))).isEqualTo(2);
            Assertions.assertThat(number(sql,
                    "SELECT COUNT(DISTINCT sequence_no) FROM travel_plan_items")).isEqualTo(2);
            Assertions.assertThat(single(sql,
                    "SELECT status FROM survey_attempts WHERE trip_id = " + tripId))
                    .isEqualTo("CANCELED");
            Assertions.assertThat(number(sql,
                    "SELECT COUNT(*) FROM question_responses")).isEqualTo(1);
            Assertions.assertThat(number(sql, """
                    SELECT COUNT(DISTINCT invite_code) FROM travel_invitations
                    WHERE trip_id = %d AND CHAR_LENGTH(invite_code) = 8
                    """.formatted(tripId))).isEqualTo(2);
            Assertions.assertThat(number(sql,
                    "SELECT CHAR_LENGTH(invite_code) FROM trips WHERE id = " + tripId)).isEqualTo(6);
            Assertions.assertThat(single(sql,
                    "SELECT invite_code FROM trips WHERE id = " + tripId))
                    .matches("[A-Z0-9]{6}");
            Assertions.assertThat(number(sql,
                    "SELECT latitude FROM places WHERE id = 1")).isEqualTo(90);
            Assertions.assertThat(number(sql,
                    "SELECT longitude FROM places WHERE id = 1")).isEqualTo(-180);
        }
        }
    }

    private long generatedId(Statement statement, String sql) throws Exception {
        statement.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
        try (ResultSet keys = statement.getGeneratedKeys()) {
            Assertions.assertThat(keys.next()).isTrue();
            return keys.getLong(1);
        }
    }

    private String single(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            Assertions.assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private int number(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            Assertions.assertThat(result.next()).isTrue();
            return result.getInt(1);
        }
    }
}
