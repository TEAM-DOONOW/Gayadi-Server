package com.gayadi.server.legal;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.legal.dto.response.LegalDocumentResponse;
import com.gayadi.server.legal.query.LegalDocumentQueryResult;
import org.springframework.stereotype.Service;

/** 법률 문서 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class LegalDocumentService {

    private final LegalDocumentRepository repository;

    public LegalDocumentService(LegalDocumentRepository repository) {
        this.repository = repository;
    }

    /** 사용자가 열람할 수 있는 게시 법률 문서를 반환합니다. */
    public LegalDocumentResponse get(String documentId) {
        if (documentId == null || !documentId.matches("[a-z0-9-]{1,50}")) {
            throw new BusinessException(LegalErrorCode.LEGAL_DOCUMENT_ID_INVALID);
        }
        return repository.findPublished(documentId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(LegalErrorCode.LEGAL_DOCUMENT_NOT_FOUND));
    }

    private LegalDocumentResponse toResponse(LegalDocumentQueryResult result) {
        return new LegalDocumentResponse(
                result.id(),
                result.title(),
                result.version(),
                result.effectiveDate(),
                result.publicationStatus(),
                result.summary(),
                result.sections().stream()
                        .map(section -> new LegalDocumentResponse.Section(section.title(), section.body()))
                        .toList(),
                result.reviewNotice());
    }
}
