package com.gayadi.server.auth;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuthErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        AuthErrorCode[] values = AuthErrorCode.values();

        assertThat(Arrays.stream(values).map(AuthErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(AuthErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(AuthErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.auth."));
    }
}
