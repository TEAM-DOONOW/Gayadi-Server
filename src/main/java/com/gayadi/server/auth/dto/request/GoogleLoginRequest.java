package com.gayadi.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** GoogleLoginRequest API 요청 데이터를 전달합니다. */
@Schema(name = "GoogleLoginRequest", description = "Google ID 토큰 로그인 요청")
public record GoogleLoginRequest(
        @Schema(
                description = "Android Google 로그인으로 받은 ID 토큰",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.auth.google-id-token.required}")
        @Size(max = 4096, message = "{validation.auth.google-id-token.size}")
        String idToken
) {
}
