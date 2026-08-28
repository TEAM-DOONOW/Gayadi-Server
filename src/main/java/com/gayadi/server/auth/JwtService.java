package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

/**
 * 경량 HMAC-SHA256 JWT 구현.
 * 운영 전환 시 표준 OAuth/OIDC principal 검증으로 교체하는 것을 전제로 둔다.
 */
@Service
public class JwtService {

    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int GENERATED_SECRET_BYTES = 32;

    private final JsonSupport json;
    private final byte[] secret;
    private final Duration expiresIn;

    public JwtService(
            JsonSupport json,
            Environment environment,
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expires-in-seconds:604800}") long expiresInSeconds) {
        String configuredSecret = secret == null ? "" : secret.trim();
        boolean weakSecret = configuredSecret.length() < GENERATED_SECRET_BYTES
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
        this.secret = configuredSecret.isEmpty()
                ? generatedDevelopmentSecret()
                : configuredSecret.getBytes(StandardCharsets.UTF_8);
        this.expiresIn = Duration.ofSeconds(expiresInSeconds);
    }

    public String issue(long userId, String email) {
        Instant now = Instant.now();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", String.valueOf(userId));
        payload.put("email", email);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", now.plus(expiresIn).getEpochSecond());

        String header = base64Url(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String body = base64Url(json.write(payload).getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + body;
        String signature = base64Url(sign(signingInput));
        return signingInput + "." + signature;
    }

    public long parseAndGetUserId(String token) {
        Map<String, Object> claims = parse(token);
        Object sub = claims.get("sub");
        if (sub == null) {
            throw unauthorized();
        }
        try {
            return Long.parseLong(sub.toString());
        } catch (NumberFormatException e) {
            throw unauthorized();
        }
    }

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
        byte[] expected = sign(signingInput);
        byte[] actual;
        try {
            actual = decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw unauthorized();
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw unauthorized();
        }

        byte[] payload;
        try {
            payload = decode(parts[1]);
        } catch (IllegalArgumentException e) {
            throw unauthorized();
        }
        Map<String, Object> claims;
        try {
            claims = json.read(new String(payload, StandardCharsets.UTF_8), Map.class);
        } catch (Exception e) {
            throw unauthorized();
        }
        try {
            long now = Instant.now().getEpochSecond();
            if (claims.get("exp") == null || toLong(claims.get("exp")) <= now) {
                throw new ApiException(HttpStatus.UNAUTHORIZED,
                        "로그인이 만료되었습니다. 다시 로그인해 주세요.");
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw unauthorized();
        }
        return claims;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private byte[] sign(String signingInput) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
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

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다.");
    }
}
