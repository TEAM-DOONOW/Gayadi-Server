package com.gayadi.server.recommendation;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

/** 추천 Agent 실행과 외부 모델 처리 오류 코드를 정의합니다. */
public enum RecommendationErrorCode implements ErrorCode {

    // Feature Availability - 추천 및 상황 대처 기능 설정
    RECOMMENDATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "RECOMMENDATION_UNAVAILABLE",
            "error.recommendation.unavailable"),
    SITUATION_AGENT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SITUATION_AGENT_UNAVAILABLE",
            "error.recommendation.situation-agent-unavailable"),
    EMBEDDING_ADMIN_FORBIDDEN(HttpStatus.FORBIDDEN, "EMBEDDING_ADMIN_FORBIDDEN",
            "error.recommendation.embedding-admin-forbidden"),
    EMBEDDING_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EMBEDDING_UNAVAILABLE",
            "error.recommendation.embedding-unavailable"),

    // External AI - 외부 언어 모델 호출 및 응답
    AI_RESPONSE_INVALID(HttpStatus.BAD_GATEWAY, "AI_RESPONSE_INVALID",
            "error.recommendation.ai-response-invalid"),
    AI_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "AI_REQUEST_FAILED",
            "error.recommendation.ai-request-failed");

    private final HttpStatus status;
    private final String code;
    private final String messageKey;

    RecommendationErrorCode(HttpStatus status, String code, String messageKey) {
        this.status = status;
        this.code = code;
        this.messageKey = messageKey;
    }

    @Override

    public HttpStatus status() {
        return status;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String messageKey() {
        return messageKey;
    }
}
