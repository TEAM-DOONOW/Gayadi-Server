package com.gayadi.server.common.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiErrorDetail", description = "요청에서 오류가 발생한 항목")
public record ApiErrorDetail(
        @Schema(description = "오류가 발생한 필드 또는 파라미터", example = "amount")
        String field,
        @Schema(description = "해당 항목을 수정하기 위한 안내", example = "경비 금액이 필수입니다.")
        String message
) {
}
