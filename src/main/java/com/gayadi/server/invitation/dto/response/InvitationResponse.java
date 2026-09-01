package com.gayadi.server.invitation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gayadi.server.invitation.model.InvitationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** InvitationResponse API 응답 데이터를 반환합니다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "InvitationResponse", description = "특정 사용자에게 발급한 여행 초대")
public record InvitationResponse(
        @Schema(description = "초대 ID", example = "27", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "여행 ID", example = "31", requiredMode = Schema.RequiredMode.REQUIRED)
        long tripId,

        @Schema(description = "초대한 사용자 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        long inviterId,

        @Schema(description = "초대한 사용자 닉네임", nullable = true)
        String inviterNickname,

        @Schema(description = "초대받은 사용자 ID", example = "18", nullable = true)
        Long inviteeId,

        @Schema(description = "초대받은 사용자 닉네임", nullable = true)
        String inviteeNickname,

        @Schema(description = "특정 사용자 초대 코드", example = "I8M3K9Q2", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,

        @Schema(description = "초대 상태", example = "PENDING", requiredMode = Schema.RequiredMode.REQUIRED)
        InvitationStatus status,

        @Schema(description = "초대 만료 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime expiresAt,

        @Schema(description = "수락 시각", nullable = true)
        LocalDateTime acceptedAt,

        @Schema(description = "거절 시각", nullable = true)
        LocalDateTime declinedAt,

        @Schema(description = "취소 시각", nullable = true)
        LocalDateTime cancelledAt,

        @Schema(description = "초대 생성 시각", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime createdAt
) {
}
