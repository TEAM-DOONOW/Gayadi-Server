package com.gayadi.server.notice;

import com.gayadi.server.notice.dto.response.NoticeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 서비스 공지 관련 HTTP 요청과 응답을 처리합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/notices")
@Tag(name = "공지", description = "앱 업데이트와 서비스 공지")
public class NoticeController {

    private final NoticeService service;

    public NoticeController(NoticeService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "공지 목록")
    @ApiResponse(responseCode = "200", description = "고정 공지와 최신 공지 목록",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = NoticeResponse.class))))
    public List<NoticeResponse> list(
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return service.list(limit, offset);
    }

    @GetMapping("/{noticeId}")
    @Operation(summary = "공지 상세")
    public NoticeResponse get(@PathVariable String noticeId) {
        return service.get(noticeId);
    }
}
