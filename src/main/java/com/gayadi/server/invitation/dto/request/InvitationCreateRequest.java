package com.gayadi.server.invitation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/** InvitationCreateRequest API 요청 데이터를 전달합니다. */
public record InvitationCreateRequest(
        @Schema(description = "초대할 사용자 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.invitation.invitee-user-id.required}")
        Long inviteeUserId,

        @Schema(description = "초대 만료 시각", example = "2026-09-01T10:30:00", nullable = true)
        @Future(message = "{validation.invitation.expires-at.future}")
        LocalDateTime expiresAt
) {
}
