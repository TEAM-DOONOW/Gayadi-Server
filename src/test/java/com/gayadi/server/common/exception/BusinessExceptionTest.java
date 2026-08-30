package com.gayadi.server.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessExceptionTest {

    @Test
    void preservesErrorCodeAndDefensivelyCopiesMessageArguments() {
        Object[] arguments = {31};
        BusinessException exception = new BusinessException(CommonErrorCode.INVALID_REQUEST, arguments);

        arguments[0] = 99;
        Object[] returned = exception.getMessageArguments();
        returned[0] = 100;

        assertThat(exception.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST);
        assertThat(exception.getMessage()).isEqualTo(CommonErrorCode.INVALID_REQUEST.defaultMessage());
        assertThat(exception.getMessageArguments()).containsExactly(31);
    }
}
