package com.gayadi.server.auth.query;

import java.util.List;

/** 인증과 사용자 계정 Repository의 UserPersonalityQueryResult 조회 결과를 전달합니다. */
public record UserPersonalityQueryResult(
        String resultCode,
        String name,
        String characterKey,
        List<String> strengths,
        List<String> weaknesses
) {
}
