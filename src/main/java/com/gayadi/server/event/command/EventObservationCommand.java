package com.gayadi.server.event.command;

import com.gayadi.server.event.model.EventType;
import com.gayadi.server.event.model.Severity;

import java.util.Map;

/** 검증된 현장 상황 입력을 EventService에 전달하는 내부 명령입니다. */
public record EventObservationCommand(
        Long placeId,
        EventType eventType,
        String source,
        Severity severity,
        Map<String, Object> values
) {
}
