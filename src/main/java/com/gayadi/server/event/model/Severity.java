package com.gayadi.server.event.model;

/** 현장 상황이 여행 일정에 미치는 심각도 단계를 나타냅니다. */
public enum Severity {
    LOW("낮음"),
    MEDIUM("보통"),
    HIGH("높음"),
    CRITICAL("매우 높음");

    private final String label;

    Severity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
