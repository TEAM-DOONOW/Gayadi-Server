package com.gayadi.server.legal.query;

import com.gayadi.server.legal.model.LegalPublicationStatus;

import java.time.LocalDate;
import java.util.List;

/** 법률 문서 Repository의 LegalDocumentQueryResult 조회 결과를 전달합니다. */
public record LegalDocumentQueryResult(
        String id,
        String title,
        String version,
        LocalDate effectiveDate,
        LegalPublicationStatus publicationStatus,
        String summary,
        List<Section> sections,
        String reviewNotice
) {
    public record Section(
            String title,
            String body
    ) {
    }
}
