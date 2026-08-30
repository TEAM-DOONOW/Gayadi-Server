package com.gayadi.server.place;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PlaceErrorCode implements ErrorCode {

    // Search - 장소 검색 조건 및 페이지 기준값
    PLACE_SEARCH_QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "PLACE_SEARCH_QUERY_TOO_LONG",
            "error.place.search-query-too-long", "장소 검색어는 100자까지 입력할 수 있습니다."),
    PLACE_REGION_TOO_LONG(HttpStatus.BAD_REQUEST, "PLACE_REGION_TOO_LONG",
            "error.place.region-too-long", "지역 이름은 50자까지 입력할 수 있습니다."),
    PLACE_CURSOR_INVALID(HttpStatus.BAD_REQUEST, "PLACE_CURSOR_INVALID",
            "error.place.cursor-invalid", "장소 목록 기준값은 1 이상이어야 합니다."),
    PLACE_CATEGORY_INVALID(HttpStatus.BAD_REQUEST, "PLACE_CATEGORY_INVALID",
            "error.place.category-invalid", "올바르지 않은 장소 분류입니다."),

    // Lookup - 공개 장소 조회
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND",
            "error.place.not-found", "공개된 장소를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    PlaceErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
        this.defaultMessage = defaultMessage;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
    @Override public String defaultMessage() { return defaultMessage; }
}
