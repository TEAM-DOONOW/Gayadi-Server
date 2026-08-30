package com.gayadi.server.common.response;

import com.gayadi.server.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** MVC와 Security가 같은 오류 응답 계약을 사용하도록 응답 생성을 담당한다. */
@Component
public class ApiErrorResponseFactory {

    public String newTraceId() {
        return UUID.randomUUID().toString();
    }

    public ApiErrorResponse create(
            ErrorCode errorCode,
            String message,
            String path,
            String traceId,
            List<ApiErrorDetail> details) {
        return create(errorCode.status(), errorCode.code(), message, path, traceId, details);
    }

    public ApiErrorResponse create(
            HttpStatus status,
            String code,
            String message,
            String path,
            String traceId,
            List<ApiErrorDetail> details) {
        return new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                path,
                traceId,
                details == null || details.isEmpty() ? null : List.copyOf(details)
        );
    }
}
