package com.gayadi.server.auth;

import com.gayadi.server.common.JsonSupport;
import com.gayadi.server.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 경량 HMAC-SHA256 JWT 구현.
 * 운영 전환 시 표준 OAuth/OIDC principal 검증으로 교체하는 것을 전제로 둔다.
 */
@Service
public class JwtService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final int GENERATED_SECRET_BYTES = 32;
    private static final long ALLOWED_CLOCK_SKEW_SECONDS = 60;

    private final JsonSupport json;
    private final Map<String, byte[]> verificationKeys;
    private final String activeKeyId;
    private final Duration expiresIn;
    private final String issuer;
    private final String audience;

    public JwtService(
            JsonSupport json,
            Environment environment,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expires-in-seconds:900}") long expiresInSeconds,
            @Value("${app.jwt.issuer:gayadi-server}") String issuer,
            @Value("${app.jwt.audience:gayadi-android}") String audience,
            @Value("${app.jwt.key-id:current}") String keyId,
            @Value("${app.jwt.previous-secret:}") String previousSecret,
            @Value("${app.jwt.previous-key-id:previous}") String previousKeyId) {
        if (expiresInSeconds <= 0) {
            throw new IllegalStateException("JWT 만료 시간은 0초보다 커야 합니다.");
        }

        String configuredSecret = secret == null ? "" : secret.trim();
        boolean weakSecret = configuredSecret.getBytes(StandardCharsets.UTF_8).length < GENERATED_SECRET_BYTES
                || configuredSecret.toLowerCase(java.util.Locale.ROOT).contains("change-me");
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod") && weakSecret) {
            throw new IllegalStateException("운영 환경에는 32자 이상의 안전한 JWT 비밀키가 필요합니다.");
        }
        String datasourceUrl = environment.getProperty("spring.datasource.url", "");
        boolean externalDatabase = !datasourceUrl.startsWith("jdbc:h2:");
        if (externalDatabase && weakSecret) {
            throw new IllegalStateException("외부 DB 환경에는 32자 이상의 안전한 JWT 비밀키가 필요합니다.");
        }
        this.json = json;
        byte[] activeSecret = configuredSecret.isEmpty()
                ? generatedDevelopmentSecret()
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
        this.activeKeyId = requireSetting(keyId, "JWT key id");
        this.verificationKeys = verificationKeys(
                activeKeyId,
                activeSecret,
                previousKeyId,
                previousSecret);
        this.expiresIn = Duration.ofSeconds(expiresInSeconds);
        this.issuer = requireSetting(issuer, "JWT issuer");
        this.audience = requireSetting(audience, "JWT audience");
    }

    /** Android 클라이언트가 API 인증에 사용할 Access Token을 발급합니다. */
    public String issue(long userId) {
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("iss", issuer);
        payload.put("aud", audience);
        payload.put("jti", UUID.randomUUID().toString());
        payload.put("token_type", ACCESS_TOKEN_TYPE);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(expiresIn).getEpochSecond());

        Map<String, Object> headerClaims = new LinkedHashMap<>();
        headerClaims.put("alg", "HS256");
        headerClaims.put("typ", "JWT");
        headerClaims.put("kid", activeKeyId);

        String header = base64Url(json.write(headerClaims).getBytes(StandardCharsets.UTF_8));
        String body = base64Url(json.write(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + body;
        String signature = base64Url(sign(signingInput, verificationKeys.get(activeKeyId)));
        return signingInput + "." + signature;
    }

    /** 사용자 식별자 관련 인증 토큰 업무를 처리합니다. */
    public long parseAndGetUserId(String token) {
        Map<String, Object> claims = parse(token);
        Object sub = claims.get("sub");
        if (sub == null) {
            throw unauthorized();
        }
        try {
            long userId = Long.parseLong(sub.toString());
            if (userId <= 0) {
                throw unauthorized();
            }
            return userId;
        } catch (NumberFormatException e) {
            throw unauthorized();
        }
    }

    /** 만료 정보를 조회합니다. */
    public Duration getExpiresIn() {
        return expiresIn;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parse(String token) {
        if (token == null || token.isBlank()) {
            throw unauthorized();
        }
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw unauthorized();
        }
        String signingInput = parts[0] + "." + parts[1];
        Map<String, Object> header = decodeJson(parts[0]);
        String keyId = header.get("kid") == null ? null : header.get("kid").toString();
        byte[] verificationKey = keyId == null ? null : verificationKeys.get(keyId);
        if (!"HS256".equals(header.get("alg"))
                || !"JWT".equals(header.get("typ"))
                || verificationKey == null) {
            throw unauthorized();
        }

        byte[] expected = sign(signingInput, verificationKey);
        byte[] actual;
        try {
            actual = decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw unauthorized();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw unauthorized();
        }

        Map<String, Object> claims = decodeJson(parts[1]);

        try {
            long now = Instant.now().getEpochSecond();
            long issuedAt = requiredLong(claims, "iat");
            long expiration = requiredLong(claims, "exp");

            if (expiration <= now) {
                throw new BusinessException(AuthErrorCode.AUTH_TOKEN_EXPIRED);
            }

            boolean invalidClaims = !issuer.equals(claims.get("iss"))
                    || !audience.equals(claims.get("aud"))
                    || !ACCESS_TOKEN_TYPE.equals(claims.get("token_type"))
                    || blank(claims.get("jti"))
                    || issuedAt > now + ALLOWED_CLOCK_SKEW_SECONDS
                    || expiration <= issuedAt;

            if (invalidClaims) {
                throw unauthorized();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
        return claims;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> decodeJson(String encoded) {
        try {
            byte[] decoded = decode(encoded);
            return json.read(new String(decoded, StandardCharsets.UTF_8), Map.class);
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
    }

    private long requiredLong(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            throw unauthorized();
        }
        return toLong(value);
    }

    private boolean blank(Object value) {
        return value == null || value.toString().isBlank();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private byte[] sign(String signingInput, byte[] signingKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("JWT 서명 생성에 실패했습니다.", e);
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private static byte[] generatedDevelopmentSecret() {
        byte[] value = new byte[GENERATED_SECRET_BYTES];
        new SecureRandom().nextBytes(value);
        return value;
    }

    private static Map<String, byte[]> verificationKeys(
            String activeKeyId,
            byte[] activeSecret,
            String previousKeyId,
            String previousSecret) {
        Map<String, byte[]> keys = new LinkedHashMap<>();
        keys.put(activeKeyId, activeSecret);

        if (previousSecret != null && !previousSecret.isBlank()) {
            String normalizedPreviousKeyId = requireSetting(previousKeyId, "JWT previous key id");
            if (activeKeyId.equals(normalizedPreviousKeyId)) {
                throw new IllegalStateException("현재 JWT key id와 이전 key id는 달라야 합니다.");
            }
            byte[] previous = previousSecret.trim().getBytes(StandardCharsets.UTF_8);
            if (previous.length < GENERATED_SECRET_BYTES) {
                throw new IllegalStateException("이전 JWT 비밀키도 32자 이상이어야 합니다.");
            }
            keys.put(normalizedPreviousKeyId, previous);
        }
        return Map.copyOf(keys);
    }

    private static String requireSetting(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " 설정이 필요합니다.");
        }
        return value.trim();
    }

    private BusinessException unauthorized() {
        return new BusinessException(AuthErrorCode.AUTH_TOKEN_INVALID);
    }
}
