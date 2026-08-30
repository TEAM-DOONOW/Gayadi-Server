package com.gayadi.server.recommendation;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum RecommendationErrorCode implements ErrorCode {

    // Feature Availability - 추천 및 상황 대처 기능 설정
    RECOMMENDATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "RECOMMENDATION_UNAVAILABLE",
            "error.recommendation.unavailable", "맞춤 장소 추천 기능이 설정되지 않았습니다."),
    SITUATION_AGENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SITUATION_AGENT_UNAVAILABLE",
            "error.recommendation.situation-agent-unavailable", "상황 대처 Agent가 설정되지 않았습니다."),
    EMBEDDING_ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "EMBEDDING_ADMIN_FORBIDDEN",
            "error.recommendation.embedding-admin-forbidden", "관리자만 장소 검색 자료를 갱신할 수 있습니다."),
    EMBEDDING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_UNAVAILABLE",
            "error.recommendation.embedding-unavailable", "장소 검색 자료 갱신 기능이 설정되지 않았습니다."),

    // External AI - 외부 언어 모델 호출 및 응답
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID",
            "error.recommendation.ai-response-invalid", "Groq가 구조화된 결과를 반환하지 않았습니다."),
    AI_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "AI_REQUEST_FAILED",
            "error.recommendation.ai-request-failed", "Groq Agent 호출에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;
    private final String defaultMessage;

    RecommendationErrorCode(HttpStatus status, String code, String messageKey, String defaultMessage) {
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
