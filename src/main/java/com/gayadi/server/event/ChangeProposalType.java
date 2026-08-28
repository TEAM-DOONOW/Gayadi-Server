package com.gayadi.server.event;

import com.gayadi.server.common.ApiException;
import org.springframework.http.HttpStatus;

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
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "사용자 요청 제안에는 현장 관측 종류가 필요합니다.");
        }
        return eventType;
    }

    public static ChangeProposalType fromEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "현장 상황 종류가 필요합니다.");
        }
        return switch (eventType) {
            case "WEATHER" -> WEATHER_CHANGE;
            case "CONGESTION" -> CONGESTION_CHANGE;
            case "TRANSPORT" -> TRANSPORT_CHANGE;
            case "CLOSURE", "DISASTER" -> USER_REQUEST;
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST, "올바르지 않은 현장 상황 종류입니다.");
        };
    }
}
