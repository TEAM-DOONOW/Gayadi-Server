package com.gayadi.server.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void masksCredentialsAndTokens() {
        String log = "password=plain-password Authorization: Bearer aaa.bbb.ccc "
                + "serviceKey=public-api-secret accessToken=jwt-value idToken=google-id-token";

        String masked = SensitiveDataMasker.mask(log);

        assertThat(masked)
                .doesNotContain("plain-password", "aaa.bbb.ccc", "public-api-secret", "jwt-value",
                        "google-id-token")
                .contains("password=[REDACTED]", "Authorization: [REDACTED]", "idToken=[REDACTED]");
    }

    @Test
    void masksPersonalInformation() {
        String log = "email=traveler@example.com phone=010-1234-5678 rrn=900101-1234567 "
                + "card=1234-5678-9012-3456";

        String masked = SensitiveDataMasker.mask(log);

        assertThat(masked)
                .doesNotContain("traveler@example.com", "010-1234-5678", "900101-1234567", "1234-5678-9012-3456")
                .contains("t***@example.com", "010-****-5678", "rrn=[REDACTED]", "1234-****-****-3456");
    }

    @Test
    void removesControlCharactersToPreventLogInjection() {
        assertThat(SensitiveDataMasker.mask("first\nforged\tentry\rnext"))
                .isEqualTo("first forged entry next");
    }

    @Test
    void acceptsNullAndEmptyValues() {
        assertThat(SensitiveDataMasker.mask(null)).isNull();
        assertThat(SensitiveDataMasker.mask("")).isEmpty();
    }
}
