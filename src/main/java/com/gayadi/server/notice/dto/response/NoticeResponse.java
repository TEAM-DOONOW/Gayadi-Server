package com.gayadi.server.notice.dto.response;

import com.gayadi.server.notice.model.NoticeCategory;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** NoticeResponse API 응답 데이터를 반환합니다. */
@Schema(name = "NoticeResponse", description = "Android 설정 화면에 표시하는 공지")
public record NoticeResponse(
        @Schema(description = "공지 ID", example = "welcome-2026", requiredMode = Schema.RequiredMode.REQUIRED)
        String id,

        @Schema(description = "공지 제목", example = "가야디 여행 기능 안내", requiredMode = Schema.RequiredMode.REQUIRED)
        String title,

        @Schema(description = "공지 카테고리", example = "update", requiredMode = Schema.RequiredMode.REQUIRED)
        NoticeCategory category,

        @Schema(description = "관련 앱 버전", example = "1.0.0", nullable = true)
        String version,

        @Schema(description = "게시 시각", example = "2026-08-31T10:30:00", requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime publishedAt,

        @Schema(description = "공지 요약", example = "가야디의 주요 여행 기능을 안내합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        String summary,

        @Schema(description = "공지 본문 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        List<Section> sections,

        @Schema(description = "상단 고정 여부", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean isPinned
) {
    public record Section(
            @Schema(description = "본문 구역 제목", example = "여행 준비", requiredMode = Schema.RequiredMode.REQUIRED)
            String title,

            @Schema(description = "본문 내용", example = "여행을 만들고 참여자를 초대할 수 있습니다.", requiredMode = Schema.RequiredMode.REQUIRED)
            String body
    ) {
    }
}
