package com.gayadi.server.common;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(name = "ApiErrorResponse", description = "요청을 처리하지 못했을 때 반환하는 공통 형식")
public record ApiErrorResponse(
        @Schema(description = "오류 발생 시각") Instant timestamp,
        @Schema(description = "HTTP 상태값", example = "400") int status,
        @Schema(description = "오류 코드", example = "BAD_REQUEST") String code,
        @Schema(description = "사용자에게 보여 줄 한국어 안내") String message,
        @Schema(description = "요청 경로") String path,
        @Schema(description = "오류 추적 식별자") String traceId,
        @Schema(description = "필드별 상세 오류", type = "object") Object details
) {
}
