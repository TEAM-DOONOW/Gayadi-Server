package com.gayadi.server.database;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ErdSchemaContractIntegrationTests {

    private static final Set<String> ORIGINAL_ERD_TABLES = Set.of(
            "users", "regions", "surveys", "survey_questions", "survey_question_options",
            "places", "trips", "trip_participants", "travel_invitations", "travel_plans",
            "travel_plan_items", "travel_routes", "travel_supplies", "survey_attempts",
            "question_responses", "event_observations", "ai_schedule_change_proposals",
            "notifications", "user_devices", "social_login_accounts", "user_favorite_places");

    private static final Set<String> SERVER_EXTENSION_TABLES = Set.of(
            "trip_cities", "legal_documents", "travel_personality_results", "friendships",
            "trip_date_availability_submissions", "trip_date_availability", "trip_expenses",
            "trip_expense_participants", "trip_shared_fund_contributions", "notices",
            "support_inquiries");

    private static final Map<String, Set<String>> REQUIRED_COLUMNS = Map.of(
            "users", Set.of("id", "nickname", "email", "password_hash", "status", "deleted_at"),
            "trips", Set.of("id", "owner_id", "start_date", "end_date", "departure_mode", "version"),
            "survey_questions", Set.of("id", "survey_id", "question_text", "axis_type", "sequence_no"),
            "travel_plans", Set.of("id", "trip_id", "plan_date", "day_number", "version"),
            "travel_routes", Set.of("id", "plan_id", "member_id", "phase", "route_data"),
            "ai_schedule_change_proposals", Set.of(
                    "id", "trip_id", "plan_id", "before_snapshot", "after_snapshot", "base_revision_no"),
            "trip_expenses", Set.of("id", "trip_id", "amount", "payment_source", "created_by"));

    private final DataSource dataSource;

    @Autowired
    ErdSchemaContractIntegrationTests(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Test
    void flywaySchemaContainsOriginalErdAndDocumentedServerExtensions() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Set<String> tables = tableNames(connection.getMetaData());
            assertThat(tables).containsAll(ORIGINAL_ERD_TABLES).containsAll(SERVER_EXTENSION_TABLES);

            for (Map.Entry<String, Set<String>> contract : REQUIRED_COLUMNS.entrySet()) {
                assertThat(columnNames(connection.getMetaData(), contract.getKey()))
                        .as("%s columns", contract.getKey())
                        .containsAll(contract.getValue());
            }
        }
    }

    private Set<String> tableNames(DatabaseMetaData metadata) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet result = metadata.getTables(null, null, "%", new String[]{"TABLE"})) {
            while (result.next()) {
                String schema = normalize(result.getString("TABLE_SCHEM"));
                if (schema == null || schema.equals("public")) {
                    names.add(normalize(result.getString("TABLE_NAME")));
                }
            }
        }
        return names;
    }

    private Set<String> columnNames(DatabaseMetaData metadata, String table) throws Exception {
        Set<String> names = new HashSet<>();
        try (ResultSet result = metadata.getColumns(null, null, table, "%")) {
            while (result.next()) names.add(normalize(result.getString("COLUMN_NAME")));
        }
        return names;
    }

    private String normalize(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
