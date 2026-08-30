package com.gayadi.server.place;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PlaceErrorCode implements ErrorCode {

    // Search - 장소 검색 조건 및 페이지 기준값
    PLACE_SEARCH_QUERY_TOO_LONG(HttpStatus.BAD_REQUEST, "PLACE_SEARCH_QUERY_TOO_LONG",
            "error.place.search-query-too-long"),
    PLACE_REGION_TOO_LONG(HttpStatus.BAD_REQUEST, "PLACE_REGION_TOO_LONG",
            "error.place.region-too-long"),
    PLACE_CURSOR_INVALID(HttpStatus.BAD_REQUEST, "PLACE_CURSOR_INVALID",
            "error.place.cursor-invalid"),
    PLACE_CATEGORY_INVALID(HttpStatus.BAD_REQUEST, "PLACE_CATEGORY_INVALID",
            "error.place.category-invalid"),

    // Lookup - 공개 장소 조회
    PLACE_NOT_FOUND(HttpStatus.NOT_FOUND, "PLACE_NOT_FOUND",
            "error.place.not-found");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    PlaceErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
