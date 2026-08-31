package com.gayadi.server.notice;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.notice.query.NoticeQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 서비스 공지 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class NoticeRepository {

    private final JdbcClient jdbc;
    private final JsonSupport json;

    public NoticeRepository(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 게시 중인 공지를 페이지 조건에 맞춰 최신순으로 조회합니다. */
    public List<NoticeQueryResult> findAllPublished(int limit, int offset) {
        return jdbc.sql("""
                SELECT * FROM notices
                WHERE status = 'PUBLISHED' AND published_at <= CURRENT_TIMESTAMP
                ORDER BY pinned DESC, published_at DESC, notice_id DESC
                LIMIT ? OFFSET ?
                """)
                .params(limit, offset)
                .query()
                .listOfRows()
                .stream()
                .map(this::map)
                .toList();
    }

    /** 게시된 정보를 DB에서 조회합니다. */
    public Optional<NoticeQueryResult> findPublished(String noticeId) {
        return jdbc.sql("""
                SELECT * FROM notices
                WHERE notice_id = ? AND status = 'PUBLISHED'
                  AND published_at <= CURRENT_TIMESTAMP
                """)
                .param(noticeId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::map);
    }

    private NoticeQueryResult map(Map<String, Object> row) {
        List<?> rawSections = json.read(RowSupport.strValue(row, "sections"), List.class);
        List<NoticeQueryResult.Section> sections = new ArrayList<>();
        for (Object rawSection : rawSections) {
            if (!(rawSection instanceof Map<?, ?> section)) {
                continue;
            }
            Object title = section.get("title");
            Object body = section.get("body");
            sections.add(new NoticeQueryResult.Section(
                    title == null ? "" : title.toString(),
                    body == null ? "" : body.toString()));
        }

        return new NoticeQueryResult(
                RowSupport.strValue(row, "notice_id"),
                RowSupport.strValue(row, "title"),
                RowSupport.strValue(row, "category").toLowerCase(Locale.ROOT),
                nullableString(row, "version"),
                AppDateFormat.databaseDateTime(RowSupport.value(row, "published_at")),
                RowSupport.strValue(row, "summary"),
                List.copyOf(sections),
                Boolean.TRUE.equals(RowSupport.value(row, "pinned")));
    }

    private String nullableString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(Locale.ROOT));
        }
        return value == null ? null : value.toString();
    }
}
