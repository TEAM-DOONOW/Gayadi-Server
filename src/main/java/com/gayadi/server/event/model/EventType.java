package com.gayadi.server.event.model;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.event.EventErrorCode;

/** 현장에서 관측할 수 있는 날씨·혼잡·교통·운영·재난 상황을 구분합니다. */
public enum EventType {
    WEATHER("날씨"),
    CONGESTION("혼잡"),
    TRANSPORT("교통"),
    CLOSURE("운영 중단"),
    DISASTER("재난");

    private final String label;

    EventType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static EventType from(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(EventErrorCode.EVENT_TYPE_REQUIRED);
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(EventErrorCode.EVENT_TYPE_INVALID);
        }
    }
}
