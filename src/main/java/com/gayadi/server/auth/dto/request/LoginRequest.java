package com.gayadi.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** LoginRequest API 요청 데이터를 전달합니다. */
public record LoginRequest(
        @Schema(description = "이메일", example = "traveler@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.auth.email.required}")
        @Email(message = "{validation.auth.email.format}")
        @Size(max = 255, message = "{validation.auth.email.size}")
        String email,

        @Schema(description = "비밀번호", example = "secure-password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.auth.password.required}")
        @Size(max = 72, message = "{validation.auth.login-password.size}")
        String password
) {
}
