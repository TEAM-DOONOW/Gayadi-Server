package com.gayadi.server.notice.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

/** 서비스 공지의 내용 분류를 나타냅니다. */
public enum NoticeCategory {
    UPDATE,
    NOTICE,
    EVENT;

    @JsonCreator
    public static NoticeCategory from(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
