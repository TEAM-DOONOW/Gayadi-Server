package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final JsonSupport json = new JsonSupport(new ObjectMapper());
    private final JwtService service = new JwtService(
            json, new MockEnvironment(), new JwtProperties(SECRET, 3600));

    @Test
    void parsesIssuedToken() {
        assertThat(service.parseAndGetUserId(service.issue(42L, "user@example.com")))
                .isEqualTo(42L);
    }

    @Test
    void rejectsSignedTokenWithMalformedExpirationAsUnauthorized() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "42");
        claims.put("exp", "not-a-number");

        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(claims)))
                .isInstanceOf(ApiException.class)
                .satisfies(exception -> assertThat(((ApiException) exception).getStatus().value())
                        .isEqualTo(401));
    }

    @Test
    void rejectsExpiredSignedTokenWithExpirationMessage() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "42");
        claims.put("exp", Instant.now().minusSeconds(1).getEpochSecond());

        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(claims)))
                .isInstanceOf(ApiException.class)
                .hasMessage("로그인이 만료되었습니다. 다시 로그인해 주세요.");
    }

    private String signedToken(Map<String, Object> claims) {
        String header = base64Url(HEADER.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(json.write(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        return signingInput + "." + base64Url(sign(signingInput));
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
