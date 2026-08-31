package com.gayadi.server.friendship.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** FriendshipCreateRequest API 요청 데이터를 전달합니다. */
public record FriendshipCreateRequest(
        @Schema(description = "친구 요청 대상 사용자 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.friendship.target-user-id.required}")
        @Positive(message = "{validation.friendship.target-user-id.positive}")
        Long targetUserId
) {
}
