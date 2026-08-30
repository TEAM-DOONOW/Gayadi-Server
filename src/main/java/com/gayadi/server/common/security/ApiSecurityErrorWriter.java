package com.gayadi.server.common.security;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.exception.ErrorCode;
import com.gayadi.server.common.response.ApiErrorResponse;
import com.gayadi.server.common.response.ApiErrorResponseFactory;
import com.gayadi.server.common.response.ApiMessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Spring Security 필터 계층의 오류를 MVC와 같은 JSON 계약으로 기록한다. */
@Component
public class ApiSecurityErrorWriter {

    private final ApiErrorResponseFactory responseFactory;
    private final JsonSupport json;
    private final ApiMessageResolver messageResolver;

    public ApiSecurityErrorWriter(
            ApiErrorResponseFactory responseFactory,
            JsonSupport json,
            ApiMessageResolver messageResolver) {
        this.responseFactory = responseFactory;
        this.json = json;
        this.messageResolver = messageResolver;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode) throws IOException {
        ApiErrorResponse body = responseFactory.create(
                errorCode,
                messageResolver.resolve(errorCode, request.getLocale()),
                request.getRequestURI(),
                responseFactory.newTraceId(),
                null);

        response.setStatus(errorCode.status().value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(json.write(body));
    }
}
