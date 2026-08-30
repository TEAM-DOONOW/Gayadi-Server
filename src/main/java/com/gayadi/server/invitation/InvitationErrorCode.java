package com.gayadi.server.invitation;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InvitationErrorCode implements ErrorCode {

    // Invitation Creation - 초대 발급 조건과 코드 생성
    INVITATION_TRIP_NOT_PLANNING(HttpStatus.CONFLICT, "INVITATION_TRIP_NOT_PLANNING",
            "error.invitation.trip-not-planning"),
    INVITEE_ALREADY_MEMBER(HttpStatus.CONFLICT, "INVITEE_ALREADY_MEMBER",
            "error.invitation.invitee-already-member"),
    INVITATION_EXPIRATION_INVALID(HttpStatus.BAD_REQUEST, "INVITATION_EXPIRATION_INVALID",
            "error.invitation.expiration-invalid"),
    INVITATION_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "INVITATION_CODE_UNAVAILABLE",
            "error.invitation.code-unavailable"),

    // Invitation Decision - 초대 거절·취소 권한과 상태
    INVITATION_DECLINE_FORBIDDEN(HttpStatus.FORBIDDEN, "INVITATION_DECLINE_FORBIDDEN",
            "error.invitation.decline-forbidden"),
    INVITATION_STATUS_NOT_PENDING(HttpStatus.CONFLICT, "INVITATION_STATUS_NOT_PENDING",
            "error.invitation.status-not-pending"),

    // Invitation Code & Join - 초대 코드 검증과 여행 참여
    INVITATION_CODE_USED_OR_EXPIRED(HttpStatus.CONFLICT, "INVITATION_CODE_USED_OR_EXPIRED",
            "error.invitation.code-used-or-expired"),
    INVITATION_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION_CODE_NOT_FOUND",
            "error.invitation.code-not-found"),
    INVITATION_CODE_FORBIDDEN(HttpStatus.FORBIDDEN, "INVITATION_CODE_FORBIDDEN",
            "error.invitation.code-forbidden"),
    INVITATION_TRIP_NOT_JOINABLE(HttpStatus.CONFLICT, "INVITATION_TRIP_NOT_JOINABLE",
            "error.invitation.trip-not-joinable"),
    INVITATION_JOIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "INVITATION_JOIN_RATE_LIMITED",
            "error.invitation.join-rate-limited"),

    // Invitation Lookup - 초대 조회
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND",
            "error.invitation.not-found");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    InvitationErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override public HttpStatus status() { return status; }
    @Override public String code() { return code; }
    @Override public String messageKey() { return messageKey; }
}
