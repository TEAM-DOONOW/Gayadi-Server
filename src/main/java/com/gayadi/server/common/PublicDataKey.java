package com.gayadi.server.common;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** 공공데이터포털 인증키를 쿼리에 한 번만 넣습니다. */
public final class PublicDataKey {

    private PublicDataKey() {
    }

    /**
     * 포털의 인코딩 키는 이미 {@code %XX} 형태입니다. 디코딩 키만 여기서 인코딩합니다.
     */
    public static String queryValue(String key) {
        String trimmed = key == null ? "" : key.trim();
        if (trimmed.indexOf('%') >= 0) {
            return trimmed;
        }
        return URLEncoder.encode(trimmed, StandardCharsets.UTF_8);
    }
}
