package com.gayadi.server.friendship;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FriendshipErrorCode implements ErrorCode {

    // Friendship Request - 친구 요청 생성 및 기존 관계 충돌
    FRIENDSHIP_SELF_REQUEST(HttpStatus.BAD_REQUEST, "FRIENDSHIP_SELF_REQUEST",
            "error.friendship.self-request", "자신에게 친구 요청을 보낼 수 없습니다."),
    FRIENDSHIP_REQUEST_PENDING(HttpStatus.CONFLICT, "FRIENDSHIP_REQUEST_PENDING",
            "error.friendship.request-pending", "이미 처리되지 않은 친구 요청이 있습니다."),
    FRIENDSHIP_ALREADY_ACCEPTED(HttpStatus.CONFLICT, "FRIENDSHIP_ALREADY_ACCEPTED",
            "error.friendship.already-accepted", "이미 친구로 등록된 사용자입니다."),
    FRIENDSHIP_REQUEST_BLOCKED(HttpStatus.CONFLICT, "FRIENDSHIP_REQUEST_BLOCKED",
            "error.friendship.request-blocked", "친구 요청을 보낼 수 없습니다."),
    FRIENDSHIP_REQUEST_CONFLICT(HttpStatus.CONFLICT, "FRIENDSHIP_REQUEST_CONFLICT",
            "error.friendship.request-conflict", "이미 친구 관계가 있거나 요청을 처리하고 있습니다."),

    // Decision & Block - 요청 결정, 차단 및 취소 권한
    FRIENDSHIP_STATUS_INVALID(HttpStatus.BAD_REQUEST, "FRIENDSHIP_STATUS_INVALID",
            "error.friendship.status-invalid", "바꿀 친구 관계 상태가 올바르지 않습니다."),
    FRIENDSHIP_DECISION_NOT_PENDING(HttpStatus.CONFLICT, "FRIENDSHIP_DECISION_NOT_PENDING",
            "error.friendship.decision-not-pending", "대기 중인 친구 요청만 수락하거나 거절할 수 있습니다."),
    FRIENDSHIP_DECISION_FORBIDDEN(HttpStatus.FORBIDDEN, "FRIENDSHIP_DECISION_FORBIDDEN",
            "error.friendship.decision-forbidden", "친구 요청을 받은 사용자만 수락하거나 거절할 수 있습니다."),
    FRIENDSHIP_ALREADY_BLOCKED(HttpStatus.CONFLICT, "FRIENDSHIP_ALREADY_BLOCKED",
            "error.friendship.already-blocked", "이미 차단한 사용자입니다."),
    FRIENDSHIP_CANCEL_FORBIDDEN(HttpStatus.FORBIDDEN, "FRIENDSHIP_CANCEL_FORBIDDEN",
            "error.friendship.cancel-forbidden", "친구 요청을 보낸 사용자만 요청을 취소할 수 있습니다."),

    // Lookup & Concurrency - 관계 조회 및 동시 변경
    FRIENDSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIENDSHIP_NOT_FOUND",
            "error.friendship.not-found", "친구 관계를 찾을 수 없습니다."),
    FRIENDSHIP_TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "FRIENDSHIP_TARGET_USER_NOT_FOUND",
            "error.friendship.target-user-not-found", "친구 요청을 보낼 사용자를 찾을 수 없습니다."),
    FRIENDSHIP_CHANGED(HttpStatus.CONFLICT, "FRIENDSHIP_CHANGED",
            "error.friendship.changed", "친구 관계가 이미 변경되었습니다. 다시 불러와 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    FriendshipErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
