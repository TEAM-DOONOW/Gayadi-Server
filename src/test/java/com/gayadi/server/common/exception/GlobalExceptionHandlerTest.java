package com.gayadi.server.common.exception;

import com.gayadi.server.common.response.ApiErrorResponse;
import com.gayadi.server.common.response.ApiErrorResponseFactory;
import com.gayadi.server.common.response.ApiMessageResolver;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void convertsBusinessExceptionIntoTheCommonResponse() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage(
                "error.test.shared-fund-balance-insufficient",
                Locale.KOREAN,
                "공동 경비 잔액이 부족합니다. 현재 잔액은 {0}원입니다.");
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new ApiErrorResponseFactory(),
                new ApiMessageResolver(messageSource));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");
        request.addPreferredLocale(Locale.KOREAN);

        ResponseEntity<ApiErrorResponse> response = handler.handleBusiness(
                new BusinessException(TestErrorCode.SHARED_FUND_BALANCE_INSUFFICIENT, 10_000), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("TEST_SHARED_FUND_BALANCE_INSUFFICIENT");
        assertThat(response.getBody().message()).isEqualTo("공동 경비 잔액이 부족합니다. 현재 잔액은 10,000원입니다.");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/test");
        assertThat(response.getBody().details()).isNull();
    }

    private enum TestErrorCode implements ErrorCode {
        SHARED_FUND_BALANCE_INSUFFICIENT;

        @Override
        public HttpStatus status() {
            return HttpStatus.CONFLICT;
        }

        @Override
        public String code() {
            return "TEST_SHARED_FUND_BALANCE_INSUFFICIENT";
        }

        @Override
        public String messageKey() {
            return "error.test.shared-fund-balance-insufficient";
        }

    }
}
