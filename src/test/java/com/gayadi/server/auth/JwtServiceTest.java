package com.gayadi.server.auth;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.exception.BusinessException;
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
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"test-current\"}";
    private static final String ISSUER = "gayadi-server";
    private static final String AUDIENCE = "gayadi-android";

    private final JsonSupport json = new JsonSupport(new ObjectMapper());
    private final JwtService service = new JwtService(
            json,
            new MockEnvironment(),
            SECRET,
            3600,
            ISSUER,
            AUDIENCE,
            "test-current",
            "",
            "test-previous");

    @Test
    void parsesIssuedToken() {
        assertThat(service.parseAndGetUserId(service.issue(42L)))
                .isEqualTo(42L);
    }

    @Test
    void issuedTokenDoesNotContainEmailAndIncludesSecurityClaims() {
        String token = service.issue(42L);
        Map<String, Object> claims = decodePart(token, 1);

        assertThat(claims)
                .doesNotContainKey("email")
                .containsEntry("iss", ISSUER)
                .containsEntry("aud", AUDIENCE)
                .containsEntry("token_type", "access");
        assertThat(claims.get("jti")).asString().isNotBlank();
    }

    @Test
    void rejectsTokenWithUnsupportedAlgorithmHeader() {
        Map<String, Object> claims = validClaims();

        assertThatThrownBy(() -> service.parseAndGetUserId(
                signedToken("{\"alg\":\"none\",\"typ\":\"JWT\",\"kid\":\"test-current\"}", claims)))
                .isInstanceOf(BusinessException.class)
                .satisfies(this::assertInvalidToken);
    }

    @Test
    void rejectsTokenWithUnknownKeyId() {
        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(
                "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"unknown\"}",
                validClaims())))
                .isInstanceOf(BusinessException.class)
                .satisfies(this::assertInvalidToken);
    }

    @Test
    void acceptsPreviousKeyDuringRotationOverlap() {
        String previousSecret = "abcdef0123456789abcdef0123456789";
        JwtService rotatingService = new JwtService(
                json,
                new MockEnvironment(),
                SECRET,
                3600,
                ISSUER,
                AUDIENCE,
                "test-current",
                previousSecret,
                "test-previous");
        String previousHeader = "{\"alg\":\"HS256\",\"typ\":\"JWT\",\"kid\":\"test-previous\"}";

        String token = signedToken(previousHeader, validClaims(), previousSecret);

        assertThat(rotatingService.parseAndGetUserId(token)).isEqualTo(42L);
    }

    @Test
    void rejectsTokenForDifferentIssuerAudienceOrPurpose() {
        Map<String, Object> wrongIssuer = validClaims();
        wrongIssuer.put("iss", "another-server");

        Map<String, Object> wrongAudience = validClaims();
        wrongAudience.put("aud", "another-client");

        Map<String, Object> refreshToken = validClaims();
        refreshToken.put("token_type", "refresh");

        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(wrongIssuer)))
                .isInstanceOf(BusinessException.class)
                .satisfies(this::assertInvalidToken);
        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(wrongAudience)))
                .isInstanceOf(BusinessException.class)
                .satisfies(this::assertInvalidToken);
        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(refreshToken)))
                .isInstanceOf(BusinessException.class)
                .satisfies(this::assertInvalidToken);
    }

    @Test
    void rejectsSignedTokenWithMalformedExpirationAsUnauthorized() {
        Map<String, Object> claims = validClaims();
        claims.put("exp", "not-a-number");

        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(claims)))
                .isInstanceOf(BusinessException.class)
                .satisfies(this::assertInvalidToken);
    }

    @Test
    void rejectsExpiredSignedTokenWithExpirationMessage() {
        Map<String, Object> claims = validClaims();
        claims.put("iat", Instant.now().minusSeconds(3600).getEpochSecond());
        claims.put("exp", Instant.now().minusSeconds(1).getEpochSecond());

        assertThatThrownBy(() -> service.parseAndGetUserId(signedToken(claims)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.AUTH_TOKEN_EXPIRED));
    }

    private String signedToken(Map<String, Object> claims) {
        return signedToken(HEADER, claims);
    }

    private String signedToken(String headerJson, Map<String, Object> claims) {
        return signedToken(headerJson, claims, SECRET);
    }

    private String signedToken(String headerJson, Map<String, Object> claims, String secret) {
        String header = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = base64Url(json.write(claims).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        return signingInput + "." + base64Url(sign(signingInput, secret));
    }

    private Map<String, Object> validClaims() {
        Instant now = Instant.now();
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", "42");
        claims.put("iss", ISSUER);
        claims.put("aud", AUDIENCE);
        claims.put("jti", "test-token-id");
        claims.put("token_type", "access");
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plusSeconds(3600).getEpochSecond());
        return claims;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodePart(String token, int index) {
        String jsonValue = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[index]),
                StandardCharsets.UTF_8);
        return json.read(jsonValue, Map.class);
    }

    private void assertInvalidToken(Throwable exception) {
        assertThat(((BusinessException) exception).getErrorCode())
                .isEqualTo(AuthErrorCode.AUTH_TOKEN_INVALID);
    }

    private byte[] sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
