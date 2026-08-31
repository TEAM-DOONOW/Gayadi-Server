package com.gayadi.server.friendship.dto.response;

import com.gayadi.server.friendship.model.FriendshipStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** FriendshipResponse API 응답 데이터를 반환합니다. */
@Schema(name = "FriendshipResponse", description = "현재 사용자 기준 친구 관계")
public record FriendshipResponse(
        @Schema(description = "친구 관계 ID", example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "상대 사용자", requiredMode = Schema.RequiredMode.REQUIRED)
        PublicUserResponse user,

        @Schema(description = "친구 관계 상태", example = "ACCEPTED", requiredMode = Schema.RequiredMode.REQUIRED)
        FriendshipStatus status,

        @Schema(description = "현재 사용자가 요청했는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean requestedByMe,

        @Schema(description = "현재 사용자가 수락·거절할 수 있는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean canDecide,

        @Schema(description = "현재 사용자가 차단했는지 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean blockedByMe,

        @Schema(description = "동시 수정 확인용 버전", example = "0", requiredMode = Schema.RequiredMode.REQUIRED)
        int version,

        @Schema(description = "관계 처리 시각", nullable = true)
        LocalDateTime decidedAt,

        @Schema(description = "관계 생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt,

        @Schema(description = "관계 수정 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime updatedAt
) {
}
