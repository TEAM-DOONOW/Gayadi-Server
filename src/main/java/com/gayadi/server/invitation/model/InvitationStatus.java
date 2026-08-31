package com.gayadi.server.invitation.model;

/** 여행 초대의 처리 상태와 사용자 결정을 나타냅니다. */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED;

    public static InvitationStatus fromDatabase(String value) {
        return switch (value) {
            case "REJECTED" -> DECLINED;
            case "CANCELED", "EXPIRED" -> CANCELLED;
            default -> valueOf(value);
        };
    }
}
