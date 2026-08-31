package com.gayadi.server.favorite;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 사용자 찜 장소 처리에서 사용하는 안정적인 오류 코드를 정의합니다. */
public enum FavoriteErrorCode implements ErrorCode {

    // Favorite Place - 찜한 장소 조회 및 삭제
    FAVORITE_PLACE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "FAVORITE_PLACE_NOT_FOUND",
            "error.favorite.place-not-found");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    FavoriteErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
