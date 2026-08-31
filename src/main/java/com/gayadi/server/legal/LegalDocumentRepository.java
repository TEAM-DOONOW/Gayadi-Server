package com.gayadi.server.legal;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.RowSupport;
import com.gayadi.server.legal.model.LegalPublicationStatus;
import com.gayadi.server.legal.query.LegalDocumentQueryResult;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** 법률 문서 SQL 실행과 DB Row 매핑을 담당합니다. */
@Repository
public class LegalDocumentRepository {

    private final JdbcClient jdbc;
    private final JsonSupport json;

    public LegalDocumentRepository(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 문서 식별자에 해당하는 게시 중인 법률 문서를 조회합니다. */
    public Optional<LegalDocumentQueryResult> findPublished(String documentId) {
        return jdbc.sql("""
                SELECT document_id, title, version, effective_date, publication_status,
                       summary, review_notice, sections
                FROM legal_documents
                WHERE document_id = ? AND publication_status = 'PUBLISHED'
                """)
                .param(documentId)
                .query()
                .listOfRows()
                .stream()
                .findFirst()
                .map(this::map);
    }

    private LegalDocumentQueryResult map(Map<String, Object> row) {
        List<?> rawSections = json.read(RowSupport.strValue(row, "sections"), List.class);
        List<LegalDocumentQueryResult.Section> sections = new ArrayList<>();
        for (Object rawSection : rawSections) {
            if (!(rawSection instanceof Map<?, ?> section)) {
                continue;
            }
            Object title = section.get("title");
            Object body = section.get("body");
            sections.add(new LegalDocumentQueryResult.Section(
                    title == null ? "" : title.toString(),
                    body == null ? "" : body.toString()));
        }

        return new LegalDocumentQueryResult(
                RowSupport.strValue(row, "document_id"),
                RowSupport.strValue(row, "title"),
                RowSupport.strValue(row, "version"),
                AppDateFormat.databaseDate(RowSupport.value(row, "effective_date")),
                LegalPublicationStatus.valueOf(RowSupport.strValue(row, "publication_status")),
                RowSupport.strValue(row, "summary"),
                List.copyOf(sections),
                nullableString(row, "review_notice"));
    }

    private String nullableString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            value = row.get(key.toUpperCase(Locale.ROOT));
        }
        return value == null ? null : value.toString();
    }
}
