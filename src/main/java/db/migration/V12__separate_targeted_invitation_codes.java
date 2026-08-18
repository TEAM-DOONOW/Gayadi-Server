package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 기존 개별 초대를 충돌 없는 8자리 코드로 정리한다. */
public class V12__separate_targeted_invitation_codes extends BaseJavaMigration {

    private static final char[] CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int MAX_ATTEMPTS = 1_000;
    private final SecureRandom random = new SecureRandom();

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        Set<String> used = tripCodes(connection);
        List<InvitationCode> invitations = invitations(connection);

        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE travel_invitations SET invite_code = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?")) {
            int pending = 0;
            for (InvitationCode invitation : invitations) {
                String normalized = invitation.code() == null
                        ? "" : invitation.code().trim().toUpperCase(Locale.ROOT);
                String replacement = normalized.matches("[A-Z0-9]{8}") && used.add(normalized)
                        ? normalized : uniqueCode(used);
                used.add(replacement);
                if (!replacement.equals(invitation.code())) {
                    update.setString(1, replacement);
                    update.setLong(2, invitation.id());
                    update.addBatch();
                    if (++pending == 500) {
                        update.executeBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) update.executeBatch();
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE travel_invitations ADD CONSTRAINT ck_invitation_code_length CHECK (CHAR_LENGTH(invite_code) = 8)");
            String database = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            String condition = database.contains("postgresql")
                    ? "invite_code ~ '^[A-Z0-9]{8}$'"
                    : "REGEXP_LIKE(invite_code, '^[A-Z0-9]{8}$')";
            statement.execute("ALTER TABLE travel_invitations ADD CONSTRAINT ck_invitation_code_format CHECK ("
                    + condition + ")");
            statement.execute("ALTER TABLE travel_invitations ADD CONSTRAINT uk_travel_invitation_invite_code UNIQUE (invite_code)");
        }
    }

    private Set<String> tripCodes(Connection connection) throws Exception {
        Set<String> codes = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT invite_code FROM trips WHERE invite_code IS NOT NULL FOR UPDATE")) {
            while (rows.next()) codes.add(rows.getString(1).toUpperCase(Locale.ROOT));
        }
        return codes;
    }

    private List<InvitationCode> invitations(Connection connection) throws Exception {
        List<InvitationCode> values = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT id, invite_code FROM travel_invitations ORDER BY id FOR UPDATE")) {
            while (rows.next()) values.add(new InvitationCode(rows.getLong("id"), rows.getString("invite_code")));
        }
        return values;
    }

    private String uniqueCode(Set<String> used) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            StringBuilder code = new StringBuilder("I");
            for (int index = 1; index < 8; index++) {
                code.append(CHARACTERS[random.nextInt(CHARACTERS.length)]);
            }
            if (!used.contains(code.toString())) return code.toString();
        }
        throw new IllegalStateException("기존 초대 코드를 안전하게 바꾸지 못했습니다.");
    }

    private record InvitationCode(long id, String code) { }
}
