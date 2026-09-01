package com.gayadi.server.invitation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** TripMembershipCreateRequest API 요청 데이터를 전달합니다. */
public record TripMembershipCreateRequest(
        @Schema(description = "여행 초대 코드", example = "A1B2C3D4", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "{validation.invitation.code.required}")
        @Pattern( regexp = "(?i)^(?:[A-Z0-9]{6}|[A-Z0-9]{8})$", message = "{validation.invitation.code.pattern}")
        String inviteCode,

        @Schema(description = "출발 장소 ID", example = "31", nullable = true)
        Long departurePlaceId,

        @Schema(description = "귀가 장소 ID", example = "32", nullable = true)
        Long returnPlaceId
) {
}
