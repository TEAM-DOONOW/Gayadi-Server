package com.gayadi.server.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ApiErrorResponse", description = "요청을 처리하지 못했을 때 반환하는 공통 형식")
public record ApiErrorResponse(
        @Schema(description = "오류 발생 시각", example = "2026-08-30T12:34:56.789Z")
        Instant timestamp,
        @Schema(description = "HTTP 상태값", example = "400")
        int status,
        @Schema(description = "클라이언트가 분기에 사용하는 오류 코드", example = "INVALID_REQUEST")
        String code,
        @Schema(description = "사용자에게 보여 줄 안전한 안내", example = "요청값이 올바르지 않습니다.")
        String message,
        @Schema(description = "Query String을 제외한 요청 경로", example = "/api/v1/trips/1/expenses")
        String path,
        @Schema(description = "서버 로그와 연결하는 오류 추적 식별자")
        String traceId,
        @Schema(description = "필드·파라미터별 상세 오류. 상세 정보가 없으면 null", nullable = true)
        List<ApiErrorDetail> details
) {
}
