package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 기존 여행에 예측할 수 없는 6자리 공유 코드를 부여한다. */
public class V14__backfill_reusable_trip_codes extends BaseJavaMigration {

    private static final char[] CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int MAX_GENERATION_ATTEMPTS = 100;
    private final SecureRandom random = new SecureRandom();

    @Override
    public void migrate(Context context) throws Exception {
        Set<String> usedCodes = new HashSet<>();
        List<TripCode> trips = new ArrayList<>();

        try (Statement statement = context.getConnection().createStatement();
             ResultSet result = statement.executeQuery("SELECT id, invite_code FROM trips ORDER BY id FOR UPDATE")) {
            while (result.next()) {
                trips.add(new TripCode(result.getLong("id"), result.getString("invite_code")));
            }
        }
        trips.stream()
                .map(TripCode::code)
                .filter(this::validCode)
                .forEach(usedCodes::add);

        try (PreparedStatement update = context.getConnection().prepareStatement(
                "UPDATE trips SET invite_code = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            int pending = 0;
            for (TripCode trip : trips) {
                if (validCode(trip.code())) continue;
                String normalized = trip.code() == null
                        ? "" : trip.code().trim().toUpperCase(Locale.ROOT);
                String code = normalized.matches("[A-Z0-9]{6}") && usedCodes.add(normalized)
                        ? normalized : uniqueCode(usedCodes);
                update.setString(1, code);
                update.setLong(2, trip.id());
                update.addBatch();
                pending++;
                if (pending == 500) {
                    verifyBatch(update.executeBatch());
                    pending = 0;
                }
                usedCodes.add(code);
            }
            if (pending > 0) verifyBatch(update.executeBatch());
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("ALTER TABLE trips ALTER COLUMN invite_code SET NOT NULL");
            String database = context.getConnection().getMetaData()
                    .getDatabaseProductName().toLowerCase(Locale.ROOT);
            String condition = database.contains("postgresql")
                    ? "invite_code ~ '^[A-Z0-9]{6}$'"
                    : "REGEXP_LIKE(invite_code, '^[A-Z0-9]{6}$')";
            statement.execute("ALTER TABLE trips ADD CONSTRAINT ck_trip_invite_code_format CHECK ("
                    + condition + ")");
        }
    }

    private boolean validCode(String code) {
        return code != null && code.matches("[A-Z0-9]{6}");
    }

    private void verifyBatch(int[] results) {
        for (int result : results) {
            if (result == 0 || result == java.sql.Statement.EXECUTE_FAILED) {
                throw new IllegalStateException("기존 여행의 공유 코드를 저장하지 못했습니다.");
            }
        }
    }

    private String uniqueCode(Set<String> usedCodes) {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder("U");
            for (int index = 1; index < 6; index++) {
                code.append(CHARACTERS[random.nextInt(CHARACTERS.length)]);
            }
            if (!usedCodes.contains(code.toString())) {
                return code.toString();
            }
        }
        throw new IllegalStateException("기존 여행의 공유 코드를 만들지 못했습니다.");
    }

    private record TripCode(
            long id,
            String code
    ) { }
}
