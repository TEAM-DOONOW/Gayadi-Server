package com.gayadi.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Access Token 재발급에 사용할 Refresh Token을 전달합니다. */
@Schema(name = "RefreshTokenRequest", description = "로그인 토큰 갱신 정보")
public record RefreshTokenRequest(
        @NotBlank(message = "{validation.auth.refresh-token.required}")
        @Schema(description = "직전 로그인 또는 갱신에서 받은 Refresh Token",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String refreshToken
) {
}
