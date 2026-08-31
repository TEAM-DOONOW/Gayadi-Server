package com.gayadi.server.notice;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.notice.dto.response.NoticeResponse;
import com.gayadi.server.notice.model.NoticeCategory;
import com.gayadi.server.notice.query.NoticeQueryResult;
import org.springframework.stereotype.Service;

import java.util.List;

/** 서비스 공지 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class NoticeService {

    private static final int MAX_LIST_SIZE = 100;

    private final NoticeRepository repository;

    public NoticeService(NoticeRepository repository) {
        this.repository = repository;
    }

    /** 공지 조건에 맞는 공지 정보를 조회합니다. */
    public List<NoticeResponse> list(int requestedLimit, int requestedOffset) {
        int limit = Math.max(1, Math.min(requestedLimit, MAX_LIST_SIZE));
        int offset = Math.max(0, requestedOffset);
        return repository.findAllPublished(limit, offset).stream()
                .map(this::toResponse)
                .toList();
    }

    /** 공지 조건에 맞는 공지 정보를 조회합니다. */
    public NoticeResponse get(String noticeId) {
        validateId(noticeId);
        return repository.findPublished(noticeId)
                .map(this::toResponse)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));
    }

    private NoticeResponse toResponse(NoticeQueryResult result) {
        return new NoticeResponse(
                result.id(),
                result.title(),
                NoticeCategory.from(result.category()),
                result.version(),
                result.publishedAt(),
                result.summary(),
                result.sections().stream()
                        .map(section -> new NoticeResponse.Section(section.title(), section.body()))
                        .toList(),
                result.pinned());
    }

    private void validateId(String noticeId) {
        if (noticeId == null || !noticeId.matches("[a-zA-Z0-9-]{1,50}")) {
            throw new BusinessException(NoticeErrorCode.NOTICE_ID_INVALID);
        }
    }
}
