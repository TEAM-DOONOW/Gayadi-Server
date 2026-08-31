package com.gayadi.server.friendship;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 친구 관계 처리에서 사용하는 안정적인 오류 코드를 정의합니다. */
public enum FriendshipErrorCode implements ErrorCode {

    // Friendship Request - 친구 요청 생성 및 기존 관계 충돌
    FRIENDSHIP_SELF_REQUEST(HttpStatus.BAD_REQUEST, "FRIENDSHIP_SELF_REQUEST",
            "error.friendship.self-request"),
    FRIENDSHIP_REQUEST_PENDING(HttpStatus.CONFLICT, "FRIENDSHIP_REQUEST_PENDING",
            "error.friendship.request-pending"),
    FRIENDSHIP_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "FRIENDSHIP_ALREADY_ACCEPTED",
            "error.friendship.already-accepted"),
    FRIENDSHIP_REQUEST_BLOCKED(HttpStatus.CONFLICT, "FRIENDSHIP_REQUEST_BLOCKED",
            "error.friendship.request-blocked"),
    FRIENDSHIP_REQUEST_CONFLICT(HttpStatus.CONFLICT, "FRIENDSHIP_REQUEST_CONFLICT",
            "error.friendship.request-conflict"),

    // Decision & Block - 요청 결정, 차단 및 취소 권한
    FRIENDSHIP_STATUS_INVALID(HttpStatus.BAD_REQUEST, "FRIENDSHIP_STATUS_INVALID",
            "error.friendship.status-invalid"),
    FRIENDSHIP_DECISION_NOT_PENDING(HttpStatus.CONFLICT, "FRIENDSHIP_DECISION_NOT_PENDING",
            "error.friendship.decision-not-pending"),
    FRIENDSHIP_DECISION_FORBIDDEN(HttpStatus.FORBIDDEN, "FRIENDSHIP_DECISION_FORBIDDEN",
            "error.friendship.decision-forbidden"),
    FRIENDSHIP_ALREADY_BLOCKED(HttpStatus.CONFLICT, "FRIENDSHIP_ALREADY_BLOCKED",
            "error.friendship.already-blocked"),
    FRIENDSHIP_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "FRIENDSHIP_CANCEL_FORBIDDEN",
            "error.friendship.cancel-forbidden"),

    // Lookup & Concurrency - 관계 조회 및 동시 변경
    FRIENDSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIENDSHIP_NOT_FOUND",
            "error.friendship.not-found"),
    FRIENDSHIP_TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIENDSHIP_TARGET_USER_NOT_FOUND",
            "error.friendship.target-user-not-found"),
    FRIENDSHIP_CHANGED(HttpStatus.CONFLICT, "FRIENDSHIP_CHANGED",
            "error.friendship.changed");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    FriendshipErrorCode(HttpStatus status, String code, String messageKey) {
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
