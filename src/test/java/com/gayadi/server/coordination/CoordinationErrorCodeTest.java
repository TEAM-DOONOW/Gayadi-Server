package com.gayadi.server.coordination;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinationErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        CoordinationErrorCode[] values = CoordinationErrorCode.values();

        assertThat(Arrays.stream(values).map(CoordinationErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(CoordinationErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(CoordinationErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.coordination."));
        assertThat(Arrays.stream(values).map(CoordinationErrorCode::defaultMessage))
                .allMatch(message -> !message.isBlank());
    }
}
