package com.gayadi.server.friendship.dto.response;

import com.gayadi.server.friendship.model.FriendshipStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** UserSearchResponse API 응답 데이터를 반환합니다. */
@Schema(name = "UserSearchResponse", description = "친구 추가용 사용자 검색 결과")
public record UserSearchResponse(
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
        String emoji,

        @Schema(description = "기존 친구 관계 ID", nullable = true)
        Long friendshipId,

        @Schema(description = "기존 친구 관계 상태", nullable = true)
        FriendshipStatus friendshipStatus,

        @Schema(description = "현재 사용자가 요청했는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean requestedByMe,

        @Schema(description = "기존 친구 관계 버전", nullable = true)
        Integer friendshipVersion
) {
}
