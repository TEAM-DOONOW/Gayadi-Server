package com.gayadi.server.notice;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.RowSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class NoticeService {

    private static final int MAX_LIST_SIZE = 100;

    private final JdbcClient jdbc;
    private final JsonSupport json;

    public NoticeService(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<NoticeResponse> list(int requestedLimit, int requestedOffset) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIST_SIZE));
        int offset = Math.max(0, requestedOffset);
        return jdbc.sql("""
                SELECT * FROM notices
                WHERE status = 'PUBLISHED' AND published_at <= CURRENT_TIMESTAMP
                ORDER BY pinned DESC, published_at DESC, notice_id DESC
                LIMIT ? OFFSET ?
                """)
                .params(limit, offset)
                .query().listOfRows().stream()
                .map(this::toResponse)
                .toList();
    }

    public NoticeResponse get(String noticeId) {
        validateId(noticeId);
        return jdbc.sql("""
                SELECT * FROM notices
                WHERE notice_id = ? AND status = 'PUBLISHED'
                  AND published_at <= CURRENT_TIMESTAMP
                """)
                .param(noticeId)
                .query().listOfRows().stream()
                .findFirst()
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));
    }

    private NoticeResponse toResponse(Map<String, Object> row) {
        List<?> rawSections = json.read(RowSupport.strValue(row, "sections"), List.class);
        List<NoticeResponse.Section> sections = new ArrayList<>();
        for (Object rawSection : rawSections) {
            if (!(rawSection instanceof Map<?, ?> section)) continue;
            Object title = section.get("title");
            Object body = section.get("body");
            sections.add(new NoticeResponse.Section(
                    title == null ? "" : title.toString(),
                    body == null ? "" : body.toString()));
        }
        return new NoticeResponse(
                RowSupport.strValue(row, "notice_id"),
                RowSupport.strValue(row, "title"),
                RowSupport.strValue(row, "category").toLowerCase(Locale.ROOT),
                nullableString(row, "version"),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "published_at")),
                RowSupport.strValue(row, "summary"),
                List.copyOf(sections),
                Boolean.TRUE.equals(RowSupport.value(row, "pinned")));
    }

    private void validateId(String noticeId) {
        if (noticeId == null || !noticeId.matches("[a-zA-Z0-9-]{1,50}")) {
            throw new BusinessException(NoticeErrorCode.NOTICE_ID_INVALID);
        }
    }

    private String nullableString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) value = row.get(key.toUpperCase(Locale.ROOT));
        return value == null ? null : value.toString();
    }

}
