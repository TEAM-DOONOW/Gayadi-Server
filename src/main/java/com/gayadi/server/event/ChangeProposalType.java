package com.gayadi.server.event;

import com.gayadi.server.common.exception.BusinessException;

/** 현장 관측 종류와 저장되는 일정 변경 제안 종류의 단일 매핑입니다. */
public enum ChangeProposalType {
    WEATHER_CHANGE("WEATHER"),
    CONGESTION_CHANGE("CONGESTION"),
    TRANSPORT_CHANGE("TRANSPORT"),
    USER_REQUEST(null);

    private final String eventType;

    ChangeProposalType(String eventType) {
        this.eventType = eventType;
    }

    public String eventType() {
        if (eventType == null) {
            throw new BusinessException(EventErrorCode.USER_REQUEST_EVENT_TYPE_REQUIRED);
        }
        return eventType;
    }

    public static ChangeProposalType fromEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new BusinessException(EventErrorCode.EVENT_TYPE_REQUIRED);
        }
        return switch (eventType) {
            case "WEATHER" -> WEATHER_CHANGE;
            case "CONGESTION" -> CONGESTION_CHANGE;
            case "TRANSPORT" -> TRANSPORT_CHANGE;
            case "CLOSURE", "DISASTER" -> USER_REQUEST;
            default -> throw new BusinessException(EventErrorCode.EVENT_TYPE_INVALID);
        };
    }
}
