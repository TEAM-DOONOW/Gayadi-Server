package com.gayadi.server.invitation.dto.request;

import com.gayadi.server.invitation.model.InvitationDecision;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/** InvitationStatusUpdateRequest API 요청 데이터를 전달합니다. */
public record InvitationStatusUpdateRequest(
        @Schema(description = "초대 처리 상태", example = "DECLINED", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "{validation.invitation.status.required}")
        InvitationDecision status
) {
}
