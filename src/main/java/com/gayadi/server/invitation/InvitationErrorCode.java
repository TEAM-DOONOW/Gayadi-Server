package com.gayadi.server.invitation;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum InvitationErrorCode implements ErrorCode {

    // Invitation Creation - 초대 발급 조건과 코드 생성
    INVITATION_TRIP_NOT_PLANNING(HttpStatus.CONFLICT, "INVITATION_TRIP_NOT_PLANNING",
            "error.invitation.trip-not-planning", "준비 중인 여행에서만 초대할 수 있습니다."),
    INVITEE_ALREADY_MEMBER(HttpStatus.CONFLICT, "INVITEE_ALREADY_MEMBER",
            "error.invitation.invitee-already-member", "이미 여행에 참여 중인 사용자입니다."),
    INVITATION_EXPIRATION_INVALID(HttpStatus.BAD_REQUEST, "INVITATION_EXPIRATION_INVALID",
            "error.invitation.expiration-invalid", "초대 만료 시각은 현재보다 뒤여야 합니다."),
    INVITATION_CODE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "INVITATION_CODE_UNAVAILABLE",
            "error.invitation.code-unavailable", "초대 코드를 만들지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // Invitation Decision - 초대 거절·취소 권한과 상태
    INVITATION_DECLINE_FORBIDDEN(HttpStatus.FORBIDDEN, "INVITATION_DECLINE_FORBIDDEN",
            "error.invitation.decline-forbidden", "초대를 받은 사용자만 거절할 수 있습니다."),
    INVITATION_STATUS_NOT_PENDING(HttpStatus.CONFLICT, "INVITATION_STATUS_NOT_PENDING",
            "error.invitation.status-not-pending", "대기 중인 초대만 상태를 바꿀 수 있습니다."),

    // Invitation Code & Join - 초대 코드 검증과 여행 참여
    INVITATION_CODE_USED_OR_EXPIRED(HttpStatus.CONFLICT, "INVITATION_CODE_USED_OR_EXPIRED",
            "error.invitation.code-used-or-expired", "이미 사용했거나 만료된 초대 코드입니다."),
    INVITATION_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION_CODE_NOT_FOUND",
            "error.invitation.code-not-found", "유효한 초대 코드를 찾을 수 없습니다."),
    INVITATION_CODE_FORBIDDEN(HttpStatus.FORBIDDEN, "INVITATION_CODE_FORBIDDEN",
            "error.invitation.code-forbidden", "다른 사용자에게 발급된 초대 코드입니다."),
    INVITATION_TRIP_NOT_JOINABLE(HttpStatus.CONFLICT, "INVITATION_TRIP_NOT_JOINABLE",
            "error.invitation.trip-not-joinable", "준비 중인 여행에만 참여할 수 있습니다."),
    INVITATION_JOIN_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "INVITATION_JOIN_RATE_LIMITED",
            "error.invitation.join-rate-limited", "초대 코드 확인 요청이 많습니다. 10분 뒤 다시 시도해 주세요."),

    // Invitation Lookup - 초대 조회
    INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "INVITATION_NOT_FOUND",
            "error.invitation.not-found", "초대를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    InvitationErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
