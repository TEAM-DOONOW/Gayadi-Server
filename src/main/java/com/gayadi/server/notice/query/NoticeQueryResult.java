package com.gayadi.server.notice.query;

import java.time.LocalDateTime;
import java.util.List;

/** 서비스 공지 Repository의 NoticeQueryResult 조회 결과를 전달합니다. */
public record NoticeQueryResult(
        String id,
        String title,
        String category,
        String version,
        LocalDateTime publishedAt,
        String summary,
        List<Section> sections,
        boolean pinned
) {
    public record Section(
            String title,
            String body
    ) {
    }
}
