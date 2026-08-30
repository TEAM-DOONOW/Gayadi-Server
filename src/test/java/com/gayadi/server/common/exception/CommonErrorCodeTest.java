package com.gayadi.server.common.exception;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CommonErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        CommonErrorCode[] values = CommonErrorCode.values();

        assertThat(Arrays.stream(values).map(CommonErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(CommonErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(CommonErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.common."));
    }
}
