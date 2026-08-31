package com.gayadi.server.friendship.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** PublicUserResponse API 응답 데이터를 반환합니다. */
@Schema(name = "PublicUserResponse", description = "다른 사용자에게 공개하는 프로필")
public record PublicUserResponse(
        @Schema(description = "사용자 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "닉네임", example = "가야디", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,

        @Schema(description = "한 줄 소개", nullable = true)
        String introduction,

        @Schema(description = "프로필 이미지 URL", nullable = true)
        String profileImageUrl,

        @Schema(description = "여행 성향 캐릭터 키", nullable = true)
        String characterKey,

        @Schema(description = "여행 성향 이모지", nullable = true)
        String emoji
) {
}
