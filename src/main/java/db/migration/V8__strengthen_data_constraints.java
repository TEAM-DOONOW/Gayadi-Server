package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 기존 자료를 먼저 정리한 뒤 API와 같은 정합성 제약을 적용한다. */
public class V8__strengthen_data_constraints extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        normalizeRegions(connection);
        Map<Long, TripRange> trips = normalizeTrips(connection);
        normalizePlaces(connection);
        normalizePlanDates(connection, trips);
        mergePlansOnSameDate(connection);
        renumberPlanDays(connection);
        renumberSequences(connection, "survey_questions", "survey_id");
        renumberSequences(connection, "survey_question_options", "question_id");
        cleanSurveyResponses(connection);
        addConstraints(connection);
    }

    private void normalizeRegions(Connection connection) throws Exception {
        Map<String, Long> canonical = new LinkedHashMap<>();
        List<RegionMerge> duplicates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT region_id, name FROM regions ORDER BY region_id")) {
            while (rows.next()) {
                long id = rows.getLong("region_id");
                String name = rows.getString("name");
                Long target = canonical.putIfAbsent(name, id);
                if (target != null) duplicates.add(new RegionMerge(id, target));
            }
        }
        for (RegionMerge merge : duplicates) {
            updateRegionReference(connection, "places", merge.sourceId(), merge.targetId());
            updateRegionReference(connection, "trips", merge.sourceId(), merge.targetId());
            updateRegionReference(connection, "event_observations", merge.sourceId(), merge.targetId());
            try (PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM regions WHERE region_id = ?")) {
                delete.setLong(1, merge.sourceId());
                delete.executeUpdate();
            }
        }
    }

    private void updateRegionReference(
            Connection connection, String table, long sourceId, long targetId) throws Exception {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET region_id = ? WHERE region_id = ?")) {
            update.setLong(1, targetId);
            update.setLong(2, sourceId);
            update.executeUpdate();
        }
    }

    private Map<Long, TripRange> normalizeTrips(Connection connection) throws Exception {
        Map<Long, Integer> joinedCounts = new HashMap<>();
        try (Statement statement = connection.createStatement();
            ResultSet rows = statement.executeQuery("""
                     SELECT trip_id, COUNT(*) AS joined_count
                     FROM trip_participants WHERE status = 'JOINED' GROUP BY trip_id
                     """)) {
            while (rows.next()) {
                int joined = rows.getInt("joined_count");
                if (joined > 100) {
                    throw new IllegalStateException(
                            "참여자가 100명을 넘는 여행이 있습니다. 참여 상태를 정리한 뒤 다시 이관해 주세요. 여행 번호: "
                                    + rows.getLong("trip_id"));
                }
                joinedCounts.put(rows.getLong("trip_id"), joined);
            }
        }

        Map<Long, TripRange> ranges = new LinkedHashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, start_date, end_date, max_members FROM trips ORDER BY id");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE trips SET start_date = ?, end_date = ?, max_members = ? WHERE id = ?")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                LocalDate first = rows.getDate("start_date").toLocalDate();
                LocalDate second = rows.getDate("end_date").toLocalDate();
                LocalDate start = first.isAfter(second) ? second : first;
                LocalDate end = first.isAfter(second) ? first : second;
                Integer maximum = (Integer) rows.getObject("max_members");
                if (maximum != null) {
                    int joined = joinedCounts.getOrDefault(id, 0);
                    maximum = Math.max(1, Math.min(100, Math.max(maximum, joined)));
                }
                update.setDate(1, Date.valueOf(start));
                update.setDate(2, Date.valueOf(end));
                if (maximum == null) update.setObject(3, null);
                else update.setInt(3, maximum);
                update.setLong(4, id);
                update.addBatch();
                ranges.put(id, new TripRange(start, end));
            }
            update.executeBatch();
        }
        return ranges;
    }

    private void normalizePlaces(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE places
                    SET latitude = CASE
                            WHEN latitude < -90 THEN -90
                            WHEN latitude > 90 THEN 90
                            ELSE latitude END,
                        longitude = CASE
                            WHEN longitude < -180 THEN -180
                            WHEN longitude > 180 THEN 180
                            ELSE longitude END
                    """);
        }
    }

    private void normalizePlanDates(Connection connection, Map<Long, TripRange> trips) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, trip_id, plan_date FROM travel_plans ORDER BY id");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE travel_plans SET plan_date = ? WHERE id = ?")) {
            while (rows.next()) {
                long planId = rows.getLong("id");
                TripRange range = trips.get(rows.getLong("trip_id"));
                LocalDate date = rows.getDate("plan_date").toLocalDate();
                LocalDate normalized = date.isBefore(range.start())
                        ? range.start() : date.isAfter(range.end()) ? range.end() : date;
                if (!normalized.equals(date)) {
                    update.setDate(1, Date.valueOf(normalized));
                    update.setLong(2, planId);
                    update.addBatch();
                }
            }
            update.executeBatch();
        }
    }

    private void mergePlansOnSameDate(Connection connection) throws Exception {
        Map<PlanDate, Long> canonical = new LinkedHashMap<>();
        List<PlanMerge> duplicates = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, trip_id, plan_date FROM travel_plans ORDER BY trip_id, plan_date, id")) {
            while (rows.next()) {
                long id = rows.getLong("id");
                PlanDate key = new PlanDate(rows.getLong("trip_id"), rows.getDate("plan_date").toLocalDate());
                Long target = canonical.putIfAbsent(key, id);
                if (target != null) duplicates.add(new PlanMerge(id, target));
            }
        }
        for (PlanMerge merge : duplicates) mergePlan(connection, merge.sourceId(), merge.targetId());
    }

    private void mergePlan(Connection connection, long sourceId, long targetId) throws Exception {
        List<Long> itemIds = new ArrayList<>();
        Set<Integer> usedSequences = new HashSet<>();
        try (PreparedStatement query = connection.prepareStatement("""
                SELECT id, sequence_no FROM travel_plan_items
                WHERE plan_id IN (?, ?)
                ORDER BY CASE WHEN plan_id = ? THEN 0 ELSE 1 END, sequence_no, id
                """)) {
            query.setLong(1, targetId);
            query.setLong(2, sourceId);
            query.setLong(3, targetId);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    itemIds.add(rows.getLong("id"));
                    usedSequences.add(rows.getInt("sequence_no"));
                }
            }
        }

        try (PreparedStatement temporaryOrder = connection.prepareStatement(
                     "UPDATE travel_plan_items SET sequence_no = ? WHERE id = ?");
             PreparedStatement finalOrder = connection.prepareStatement(
                     "UPDATE travel_plan_items SET sequence_no = ? WHERE id = ?")) {
            int temporarySequence = Integer.MIN_VALUE;
            for (int index = 0; index < itemIds.size(); index++) {
                while (usedSequences.contains(temporarySequence)) {
                    if (temporarySequence == Integer.MAX_VALUE) {
                        throw new IllegalStateException("일정 순서를 이관할 임시 번호를 만들지 못했습니다.");
                    }
                    temporarySequence++;
                }
                temporaryOrder.setInt(1, temporarySequence);
                temporaryOrder.setLong(2, itemIds.get(index));
                temporaryOrder.addBatch();
                usedSequences.add(temporarySequence++);
            }
            temporaryOrder.executeBatch();

            updatePlanReference(connection, "travel_plan_items", "plan_id", sourceId, targetId);
            updatePlanReference(connection, "travel_routes", "plan_id", sourceId, targetId);
            updatePlanReference(connection, "ai_schedule_change_proposals", "plan_id", sourceId, targetId);
            updatePlanReference(connection, "notifications", "plan_id", sourceId, targetId);

            for (int index = 0; index < itemIds.size(); index++) {
                finalOrder.setInt(1, index + 1);
                finalOrder.setLong(2, itemIds.get(index));
                finalOrder.addBatch();
            }
            finalOrder.executeBatch();
        }
        try (PreparedStatement expire = connection.prepareStatement("""
                UPDATE ai_schedule_change_proposals
                SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id = ? AND status = 'PENDING'
                """)) {
            expire.setLong(1, targetId);
            expire.executeUpdate();
        }
        try (PreparedStatement expire = connection.prepareStatement("""
                UPDATE travel_routes SET status = 'EXPIRED', updated_at = CURRENT_TIMESTAMP
                WHERE plan_id = ? AND status IN ('RECOMMENDED', 'SELECTED')
                """)) {
            expire.setLong(1, targetId);
            expire.executeUpdate();
        }
        try (PreparedStatement delete = connection.prepareStatement("DELETE FROM travel_plans WHERE id = ?")) {
            delete.setLong(1, sourceId);
            delete.executeUpdate();
        }
    }

    private void updatePlanReference(
            Connection connection, String table, String column, long sourceId, long targetId) throws Exception {
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET " + column + " = ? WHERE " + column + " = ?")) {
            update.setLong(1, targetId);
            update.setLong(2, sourceId);
            update.executeUpdate();
        }
    }

    private void renumberPlanDays(Connection connection) throws Exception {
        long currentTripId = Long.MIN_VALUE;
        int dayNumber = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, trip_id FROM travel_plans ORDER BY trip_id, plan_date, id");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE travel_plans SET day_number = ? WHERE id = ?")) {
            while (rows.next()) {
                long tripId = rows.getLong("trip_id");
                if (tripId != currentTripId) {
                    currentTripId = tripId;
                    dayNumber = 0;
                }
                update.setInt(1, ++dayNumber);
                update.setLong(2, rows.getLong("id"));
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void renumberSequences(Connection connection, String table, String groupColumn) throws Exception {
        long currentGroupId = Long.MIN_VALUE;
        int sequence = 0;
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, " + groupColumn + " FROM " + table
                             + " ORDER BY " + groupColumn + ", sequence_no, id");
             PreparedStatement temporary = connection.prepareStatement(
                     "UPDATE " + table + " SET sequence_no = ? WHERE id = ?");
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE " + table + " SET sequence_no = ? WHERE id = ?")) {
            List<SequenceValue> values = new ArrayList<>();
            while (rows.next()) {
                long groupId = rows.getLong(groupColumn);
                if (groupId != currentGroupId) {
                    currentGroupId = groupId;
                    sequence = 0;
                }
                values.add(new SequenceValue(rows.getLong("id"), ++sequence));
            }
            for (SequenceValue value : values) {
                temporary.setInt(1, -value.sequence());
                temporary.setLong(2, value.id());
                temporary.addBatch();
            }
            temporary.executeBatch();
            for (SequenceValue value : values) {
                update.setInt(1, value.sequence());
                update.setLong(2, value.id());
                update.addBatch();
            }
            update.executeBatch();
        }
    }

    private void cleanSurveyResponses(Connection connection) throws Exception {
        Map<Long, Long> optionQuestions = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, question_id FROM survey_question_options")) {
            while (rows.next()) optionQuestions.put(rows.getLong("id"), rows.getLong("question_id"));
        }

        Set<ResponseKey> seen = new HashSet<>();
        Set<Long> invalidAttempts = new HashSet<>();
        List<Long> deleteIds = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("""
                     SELECT id, attempt_id, question_id, option_id
                     FROM question_responses ORDER BY id DESC
                     """)) {
            while (rows.next()) {
                long id = rows.getLong("id");
                long attemptId = rows.getLong("attempt_id");
                long questionId = rows.getLong("question_id");
                Long optionQuestion = optionQuestions.get(rows.getLong("option_id"));
                ResponseKey key = new ResponseKey(attemptId, questionId);
                if (optionQuestion == null || optionQuestion != questionId || !seen.add(key)) {
                    invalidAttempts.add(attemptId);
                    deleteIds.add(id);
                }
            }
        }

        Set<String> resultCodes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT result_code FROM travel_personality_results")) {
            while (rows.next()) resultCodes.add(rows.getString(1));
        }
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, result_code FROM survey_attempts WHERE result_code IS NOT NULL")) {
            while (rows.next()) {
                if (!resultCodes.contains(rows.getString("result_code"))) {
                    invalidAttempts.add(rows.getLong("id"));
                }
            }
        }

        try (PreparedStatement delete = connection.prepareStatement(
                     "DELETE FROM question_responses WHERE id = ?");
             PreparedStatement cancel = connection.prepareStatement("""
                     UPDATE survey_attempts
                     SET status = 'CANCELED', result_code = NULL, updated_at = CURRENT_TIMESTAMP
                     WHERE id = ?
                     """)) {
            for (Long id : deleteIds) {
                delete.setLong(1, id);
                delete.addBatch();
            }
            delete.executeBatch();
            for (Long attemptId : invalidAttempts) {
                cancel.setLong(1, attemptId);
                cancel.addBatch();
            }
            cancel.executeBatch();
        }
    }

    private void addConstraints(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE trips ADD CONSTRAINT ck_trip_date_order CHECK (start_date <= end_date)");
            statement.execute("CREATE UNIQUE INDEX uk_regions_name ON regions (name)");
            statement.execute("ALTER TABLE trips ADD CONSTRAINT ck_trip_max_members CHECK (max_members IS NULL OR max_members BETWEEN 1 AND 100)");
            statement.execute("ALTER TABLE places ADD CONSTRAINT ck_place_latitude CHECK (latitude BETWEEN -90 AND 90)");
            statement.execute("ALTER TABLE places ADD CONSTRAINT ck_place_longitude CHECK (longitude BETWEEN -180 AND 180)");
            statement.execute("ALTER TABLE travel_plans ADD CONSTRAINT ck_travel_plan_day CHECK (day_number > 0)");
            statement.execute("ALTER TABLE travel_plans ADD CONSTRAINT uk_travel_plan_trip_day UNIQUE (trip_id, day_number)");
            statement.execute("ALTER TABLE travel_plans ADD CONSTRAINT uk_travel_plan_trip_date UNIQUE (trip_id, plan_date)");
            statement.execute("ALTER TABLE survey_questions ADD CONSTRAINT uk_survey_question_sequence UNIQUE (survey_id, sequence_no)");
            statement.execute("ALTER TABLE survey_question_options ADD CONSTRAINT uk_survey_option_sequence UNIQUE (question_id, sequence_no)");
            statement.execute("ALTER TABLE survey_question_options ADD CONSTRAINT uk_survey_option_question UNIQUE (id, question_id)");
            statement.execute("ALTER TABLE question_responses ADD CONSTRAINT uk_question_response_attempt UNIQUE (attempt_id, question_id)");
            statement.execute("ALTER TABLE question_responses ADD CONSTRAINT fk_response_option_question FOREIGN KEY (option_id, question_id) REFERENCES survey_question_options (id, question_id)");
            statement.execute("ALTER TABLE survey_attempts ADD CONSTRAINT fk_attempt_personality_result FOREIGN KEY (result_code) REFERENCES travel_personality_results (result_code)");
        }
    }

    private record TripRange(LocalDate start, LocalDate end) { }
    private record PlanDate(long tripId, LocalDate date) { }
    private record PlanMerge(long sourceId, long targetId) { }
    private record SequenceValue(long id, int sequence) { }
    private record ResponseKey(long attemptId, long questionId) { }
    private record RegionMerge(long sourceId, long targetId) { }
}
