package com.gayadi.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** SignupRequest API 요청 데이터를 전달합니다. */
public record SignupRequest(
        @Schema(description = "이메일", example = "traveler@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.auth.email.required}")
        @Email(message = "{validation.auth.email.format}")
        @Size(max = 255, message = "{validation.auth.email.size}")
        String email,

        @Schema(description = "비밀번호", example = "secure-password", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.auth.password.required}")
        @Size(min = 6, max = 72, message = "{validation.auth.password.size}")
        String password,

        @Schema(description = "닉네임", example = "가야디", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.auth.nickname.required}")
        @Size(max = 10, message = "{validation.auth.nickname.size}")
        @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$", message = "{validation.auth.nickname.pattern}")
        String nickname
) {
}
