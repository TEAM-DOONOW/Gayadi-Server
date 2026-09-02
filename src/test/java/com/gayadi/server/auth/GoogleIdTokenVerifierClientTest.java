package com.gayadi.server.auth;

import com.gayadi.server.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoogleIdTokenVerifierClientTest {

    private final GoogleIdTokenVerifierClient client = new GoogleIdTokenVerifierClient(
            "test-client.apps.googleusercontent.com", "");

    @Test
    void rejectsMalformedTokenWithoutCallingGoogle() {
        assertInvalid("invalid");
        assertInvalid("aaa.bbb.ccc");
    }

    @Test
    void rejectsExpiredTokenWithoutCallingGoogle() {
        String header = encode("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        String payload = encode("{\"iss\":\"https://accounts.google.com\","
                + "\"aud\":\"test-client.apps.googleusercontent.com\","
                + "\"sub\":\"google-sub\",\"exp\":1}");
        String token = header + "." + payload + "." + encode("signature");

        assertThatThrownBy(() -> client.verify(token))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.AUTH_GOOGLE_TOKEN_EXPIRED));
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> client.verify(token))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(AuthErrorCode.AUTH_GOOGLE_TOKEN_INVALID));
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
