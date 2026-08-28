package com.gayadi.server.notice;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "NoticeResponse", description = "Android 설정 화면에 표시하는 공지")
public record NoticeResponse(
        String id,
        String title,
        String category,
        String version,
        LocalDateTime publishedAt,
        String summary,
        List<Section> sections,
        boolean isPinned
) {
    public record Section(String title, String body) {
    }
}
