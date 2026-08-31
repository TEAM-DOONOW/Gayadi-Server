package com.gayadi.server.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** UpdateProfileRequest API 요청 데이터를 전달합니다. */
public record UpdateProfileRequest(
        @Schema(description = "닉네임", example = "가야디", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.auth.nickname.required}")
        @Size(max = 10, message = "{validation.auth.nickname.size}")
        String nickname,

        @Schema(description = "한 줄 소개", example = "천천히 걷는 여행을 좋아해요.", nullable = true)
        @Size(max = 20, message = "{validation.auth.introduction.size}")
        String introduction
) {
}
