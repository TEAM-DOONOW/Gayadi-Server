package com.gayadi.server.legal;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.RowSupport;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class LegalDocumentService {

    private final JdbcClient jdbc;
    private final JsonSupport json;

    public LegalDocumentService(JdbcClient jdbc, JsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Map<String, Object> get(String documentId) {
        if (documentId == null || !documentId.matches("[a-z0-9-]{1,50}")) {
            throw new BusinessException(LegalErrorCode.LEGAL_DOCUMENT_ID_INVALID);
        }
        Map<String, Object> row = jdbc.sql("""
                SELECT document_id, title, version, effective_date, publication_status,
                       summary, review_notice, sections
                FROM legal_documents
                WHERE document_id = ? AND publication_status = 'PUBLISHED'
                """)
                .param(documentId)
                .query().listOfRows().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException(LegalErrorCode.LEGAL_DOCUMENT_NOT_FOUND));

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("id", RowSupport.strValue(row, "document_id"));
        document.put("title", RowSupport.strValue(row, "title"));
        document.put("version", RowSupport.strValue(row, "version"));
        document.put("effectiveDate", RowSupport.value(row, "effective_date"));
        document.put("publicationStatus", RowSupport.strValue(row, "publication_status"));
        document.put("summary", RowSupport.strValue(row, "summary"));
        document.put("sections", json.read(RowSupport.strValue(row, "sections"), List.class));
        Object reviewNotice = row.get("review_notice");
        if (reviewNotice == null) reviewNotice = row.get("REVIEW_NOTICE");
        document.put("reviewNotice", reviewNotice);
        return document;
    }
}
