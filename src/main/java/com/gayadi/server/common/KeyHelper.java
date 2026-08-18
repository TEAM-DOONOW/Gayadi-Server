package com.gayadi.server.common;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.OptionalLong;

@Component
public class KeyHelper {

    private final JdbcTemplate jdbcTemplate;

    public KeyHelper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long insert(String sql, Object... params) {
        var keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("저장한 자료의 식별자를 확인하지 못했습니다.");
        }
        return key.longValue();
    }

    /**
     * 현재 트랜잭션 안에 저장점을 만든 뒤 자료를 넣는다.
     *
     * <p>PostgreSQL은 유일 제약 위반 뒤 트랜잭션을 그대로 쓸 수 없으므로, 충돌한 문장만
     * 저장점까지 되돌린다. 호출부는 빈 결과를 받으면 새 값으로 다시 시도하거나 이미 저장된
     * 행을 조회할 수 있다.</p>
     */
    public OptionalLong insertOrEmptyOnUniqueViolation(String sql, Object... params) {
        OptionalLong result = jdbcTemplate.execute((ConnectionCallback<OptionalLong>) connection -> {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("저장점 저장은 트랜잭션 안에서만 사용할 수 있습니다.");
            }
            Savepoint savepoint = connection.setSavepoint();
            try (PreparedStatement statement = insertStatement(connection, sql, params)) {
                statement.executeUpdate();
                long id = generatedId(statement);
                releaseQuietly(connection, savepoint);
                return OptionalLong.of(id);
            } catch (SQLException exception) {
                connection.rollback(savepoint);
                releaseQuietly(connection, savepoint);
                if (isUniqueViolation(exception)) {
                    return OptionalLong.empty();
                }
                throw exception;
            }
        });
        if (result == null) {
            throw new IllegalStateException("저장 결과를 확인하지 못했습니다.");
        }
        return result;
    }

    private PreparedStatement insertStatement(Connection connection, String sql, Object[] params)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql, new String[]{"id"});
        for (int index = 0; index < params.length; index++) {
            statement.setObject(index + 1, params[index]);
        }
        return statement;
    }

    private long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (!keys.next()) {
                throw new IllegalStateException("저장한 자료의 식별자를 확인하지 못했습니다.");
            }
            return keys.getLong(1);
        }
    }

    private boolean isUniqueViolation(SQLException exception) {
        for (SQLException current = exception; current != null; current = current.getNextException()) {
            if ("23505".equals(current.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    private void releaseQuietly(Connection connection, Savepoint savepoint) {
        try {
            connection.releaseSavepoint(savepoint);
        } catch (SQLException ignored) {
            // 충돌 문장은 이미 저장점까지 되돌렸으므로 저장점 해제 실패는 처리 결과에 영향을 주지 않는다.
        }
    }
}
