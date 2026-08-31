package com.gayadi.server.friendship.dto.request;

import com.gayadi.server.friendship.model.FriendshipStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** FriendshipStatusUpdateRequest API 요청 데이터를 전달합니다. */
public record FriendshipStatusUpdateRequest(
        @Schema(description = "변경할 친구 관계 상태", example = "ACCEPTED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.friendship.status.required}")
        FriendshipStatus status,

        @Schema(description = "동시 수정 확인용 버전", example = "0", nullable = true)
        @PositiveOrZero(message = "{validation.friendship.version.positive-or-zero}")
        Integer version
) {
}
