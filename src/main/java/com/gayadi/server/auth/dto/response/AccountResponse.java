package com.gayadi.server.auth.dto.response;

import com.gayadi.server.auth.model.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** AccountResponse API 응답 데이터를 반환합니다. */
@Schema(name = "AccountResponse", description = "로그인한 계정의 기본 정보")
public record AccountResponse(
        @Schema(description = "사용자 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "닉네임", example = "가야디", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,

        @Schema(description = "이메일", example = "traveler@example.com", nullable = true)
        String email,

        @Schema(description = "한 줄 소개", nullable = true)
        String introduction,

        @Schema(description = "프로필 이미지 URL", nullable = true)
        String profileImageUrl,

        @Schema(description = "계정 상태", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
        UserStatus status,

        @Schema(description = "마지막 로그인 시각", nullable = true)
        LocalDateTime lastLoginAt,

        @Schema(description = "계정 생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt,

        @Schema(description = "계정 수정 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime updatedAt
) {
}
