package com.gayadi.server.legal.dto.response;

import com.gayadi.server.legal.model.LegalPublicationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

/** LegalDocumentResponse API 응답 데이터를 반환합니다. */
@Schema(name = "LegalDocumentResponse", description = "공개 중인 법률 문서")
public record LegalDocumentResponse(
        @Schema(description = "문서 ID", example = "privacy-policy", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @Schema(description = "문서 제목", example = "가야디 개인정보처리방침", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "문서 버전", example = "2.0.0", requiredMode = Schema.RequiredMode.REQUIRED)
        String version,

        @Schema(description = "시행일", example = "2026-08-13", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDate effectiveDate,

        @Schema(description = "공개 상태", example = "PUBLISHED", requiredMode = Schema.RequiredMode.REQUIRED)
        LegalPublicationStatus publicationStatus,

        @Schema(description = "문서 요약", example = "서비스가 처리하는 정보와 이용자의 권리를 안내합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        String summary,

        @Schema(description = "문서 본문 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Section> sections,

        @Schema(description = "검토 안내", example = "변경된 내용을 확인해 주세요.", nullable = true)
        String reviewNotice
) {
    public record Section(
            @Schema(description = "본문 구역 제목", example = "1. 처리 목적", requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(description = "본문 내용", example = "서비스 제공에 필요한 정보를 처리합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
            String body
    ) {
    }
}
