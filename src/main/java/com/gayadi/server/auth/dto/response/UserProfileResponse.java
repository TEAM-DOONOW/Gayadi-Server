package com.gayadi.server.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** UserProfileResponse API 응답 데이터를 반환합니다. */
@Schema(name = "UserProfileResponse", description = "현재 사용자의 공개 프로필과 최신 여행 성향")
public record UserProfileResponse(
        @Schema(description = "사용자 ID", example = "12", requiredMode = Schema.RequiredMode.REQUIRED)
        long id,

        @Schema(description = "이메일", example = "traveler@example.com", nullable = true)
        String email,

        @Schema(description = "닉네임", example = "가야디", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,

        @Schema(description = "한 줄 소개", nullable = true)
        String introduction,

        @Schema(description = "프로필 이미지 URL", nullable = true)
        String profileImageUrl,

        @Schema(description = "최신 성향 결과 코드", example = "PNR", nullable = true)
        String resultCode,

        @Schema(description = "최신 성향 이름", nullable = true)
        String travelStyleName,

        @Schema(description = "앱 캐릭터 자료 식별자", example = "character_pnr", nullable = true)
        String characterKey,

        @Schema(description = "성향의 강점", nullable = true)
        List<String> strengths,

        @Schema(description = "성향에서 주의할 점", nullable = true)
        List<String> weaknesses
) {
}
