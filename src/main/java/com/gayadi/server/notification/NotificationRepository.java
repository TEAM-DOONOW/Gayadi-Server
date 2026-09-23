package com.gayadi.server.notification;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.KeyHelper;
import com.gayadi.server.common.RowSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public class NotificationRepository {
    private final JdbcClient jdbc;
    private final KeyHelper keys;

    public NotificationRepository(JdbcClient jdbc, KeyHelper keys) {
        this.jdbc = jdbc;
        this.keys = keys;
    }

    public long create(long userId, String type, String title, String content, Long tripId, Long invitationId) {
        return keys.insert("""
                INSERT INTO notifications (user_id, notification_type, title, content, trip_id, invitation_id)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, type, title, content, tripId, invitationId);
    }

    public List<NotificationItem> list(long userId, int limit, int offset) {
        return jdbc.sql("""
                SELECT id, notification_type, title, content, trip_id, invitation_id, is_read, created_at
                FROM notifications WHERE user_id = ?
                ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """)
                .params(userId, limit, offset)
                .query().listOfRows().stream().map(this::map).toList();
    }

    public boolean markRead(long userId, long notificationId) {
        return jdbc.sql("""
                UPDATE notifications SET is_read = TRUE, read_at = COALESCE(read_at, CURRENT_TIMESTAMP)
                WHERE id = ? AND user_id = ?
                """)
                .params(notificationId, userId).update() > 0;
    }

    public void saveToken(long userId, String token) {
        if (jdbc.sql("""
                UPDATE fcm_device_tokens SET user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE token = ?
                """).params(userId, token).update() > 0) return;
        try {
            jdbc.sql("INSERT INTO fcm_device_tokens (token, user_id) VALUES (?, ?)")
                    .params(token, userId).update();
        } catch (DuplicateKeyException duplicate) {
            jdbc.sql("UPDATE fcm_device_tokens SET user_id = ?, updated_at = CURRENT_TIMESTAMP WHERE token = ?")
                    .params(userId, token).update();
        }
    }

    public void removeToken(long userId, String token) {
        jdbc.sql("DELETE FROM fcm_device_tokens WHERE user_id = ? AND token = ?")
                .params(userId, token).update();
    }

    public List<String> tokens(long userId) {
        return jdbc.sql("SELECT token FROM fcm_device_tokens WHERE user_id = ?")
                .param(userId).query(String.class).list();
    }

    private NotificationItem map(Map<String, Object> row) {
        Object trip = row.getOrDefault("trip_id", row.get("TRIP_ID"));
        Object invitation = row.getOrDefault("invitation_id", row.get("INVITATION_ID"));
        return new NotificationItem(
                RowSupport.longValue(row, "id"),
                RowSupport.strValue(row, "notification_type"),
                RowSupport.strValue(row, "title"),
                RowSupport.strValue(row, "content"),
                trip == null ? null : ((Number) trip).longValue(),
                invitation == null ? null : ((Number) invitation).longValue(),
                Boolean.TRUE.equals(RowSupport.value(row, "is_read")),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "created_at")));
    }

    public record NotificationItem(long id, String type, String title, String content,
                                   Long tripId, Long invitationId, boolean isRead, LocalDateTime createdAt) {
    }
}
