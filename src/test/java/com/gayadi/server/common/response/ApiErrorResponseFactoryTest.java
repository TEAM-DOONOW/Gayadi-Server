package com.gayadi.server.common.response;

import com.gayadi.server.common.exception.CommonErrorCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorResponseFactoryTest {

    private final ApiErrorResponseFactory factory = new ApiErrorResponseFactory();

    @Test
    void createsAnImmutableResponseFromAnErrorCode() {
        List<ApiErrorDetail> details = new ArrayList<>();
        details.add(new ApiErrorDetail("amount", "경비 금액이 필수입니다."));

        ApiErrorResponse response = factory.create(
                CommonErrorCode.INVALID_REQUEST,
                CommonErrorCode.INVALID_REQUEST.defaultMessage(),
                "/api/v1/expenses",
                "trace-123",
                details);
        details.clear();

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("INVALID_REQUEST");
        assertThat(response.path()).isEqualTo("/api/v1/expenses");
        assertThat(response.traceId()).isEqualTo("trace-123");
        assertThat(response.details()).containsExactly(
                new ApiErrorDetail("amount", "경비 금액이 필수입니다."));
    }

    @Test
    void convertsEmptyDetailsToNullAndCreatesTraceIds() {
        ApiErrorResponse response = factory.create(
                CommonErrorCode.INTERNAL_SERVER_ERROR,
                CommonErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(),
                "/api/v1/test",
                factory.newTraceId(),
                List.of());

        assertThat(response.traceId()).isNotBlank();
        assertThat(response.details()).isNull();
    }
}
