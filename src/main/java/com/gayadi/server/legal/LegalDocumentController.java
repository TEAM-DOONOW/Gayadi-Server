package com.gayadi.server.legal;

import com.gayadi.server.config.ApiSuccessSchemas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/legal-documents")
@Tag(name = "법률 문서", description = "공개 중인 이용약관과 개인정보처리방침을 제공합니다.")
public class LegalDocumentController {

    private final LegalDocumentService service;

    public LegalDocumentController(LegalDocumentService service) {
        this.service = service;
    }

    @GetMapping("/{documentId}")
    @Operation(summary = "법률 문서 조회")
    @ApiResponse(responseCode = "200", description = "현재 공개 중인 법률 문서입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.LegalDocument.class)))
    public Map<String, Object> legalDocument(@PathVariable String documentId) {
        return service.get(documentId);
    }
}
