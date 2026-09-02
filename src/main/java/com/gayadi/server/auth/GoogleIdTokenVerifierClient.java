package com.gayadi.server.auth;

import com.gayadi.server.auth.model.GoogleIdentity;
import com.gayadi.server.common.exception.BusinessException;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.HttpResponseException;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Google 공개키로 ID 토큰의 서명·만료·issuer·audience를 검증합니다. */
@Component
class GoogleIdTokenVerifierClient implements GoogleIdTokenClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdTokenVerifierClient.class);
    private static final int MAX_PROVIDER_NAME_LENGTH = 100;

    private final GoogleIdTokenVerifier verifier;

    GoogleIdTokenVerifierClient(
            @Value("${auth.google.client-id:}") String clientId,
            @Value("${auth.google.android-client-id:}") String androidClientId) {
        Set<String> acceptedAudiences = audiences(clientId, androidClientId);
        this.verifier = acceptedAudiences.isEmpty()
                ? null
                : new GoogleIdTokenVerifier.Builder(
                        new NetHttpTransport(), GsonFactory.getDefaultInstance())
                        .setAudience(acceptedAudiences)
                        .build();
    }

    @Override
    public boolean isConfigured() {
        return verifier != null;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (!isConfigured()) {
            throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_NOT_CONFIGURED);
        }
        GoogleIdToken token;
        try {
            token = GoogleIdToken.parse(GsonFactory.getDefaultInstance(), idToken);
        } catch (IOException | IllegalArgumentException exception) {
            throw invalid();
        }
        Long expiresAt = token.getPayload().getExpirationTimeSeconds();
        if (expiresAt != null && expiresAt <= Instant.now().getEpochSecond()) {
            throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_TOKEN_EXPIRED);
        }
        try {
            if (!verifier.verify(token)) {
                throw invalid();
            }
        } catch (GeneralSecurityException exception) {
            throw invalid();
        } catch (HttpResponseException exception) {
            if (exception.getStatusCode() == 429) {
                throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_RATE_LIMITED);
            }
            log.warn("Google ID 토큰 공개키 확인에 실패했습니다.");
            throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_REQUEST_FAILED);
        } catch (IOException exception) {
            log.warn("Google ID 토큰 공개키 확인에 실패했습니다.");
            throw new BusinessException(AuthErrorCode.AUTH_GOOGLE_REQUEST_FAILED);
        }
        GoogleIdToken.Payload payload = token.getPayload();
        String subject = trimToEmpty(payload.getSubject());
        if (subject.isEmpty()) {
            throw invalid();
        }
        String email = normalizeEmail(payload.getEmail());
        return new GoogleIdentity(
                subject,
                email.isEmpty() ? null : email,
                Boolean.TRUE.equals(payload.getEmailVerified()),
                truncateToNull(stringClaim(payload, "name"), MAX_PROVIDER_NAME_LENGTH),
                pictureUrl(stringClaim(payload, "picture")));
    }

    private static Set<String> audiences(String clientId, String androidClientId) {
        Set<String> audiences = new LinkedHashSet<>();
        addAudience(audiences, clientId);
        addAudience(audiences, androidClientId);
        return Set.copyOf(audiences);
    }

    private static void addAudience(Set<String> audiences, String value) {
        if (value == null) {
            return;
        }
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                audiences.add(trimmed);
            }
        }
    }

    private static String stringClaim(GoogleIdToken.Payload payload, String name) {
        Object value = payload.get(name);
        return value == null ? "" : value.toString().trim();
    }

    private static String normalizeEmail(String email) {
        return trimToEmpty(email).toLowerCase(Locale.ROOT);
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String truncateToNull(String value, int maxCodePoints) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        int codePointCount = trimmed.codePointCount(0, trimmed.length());
        if (codePointCount <= maxCodePoints) {
            return trimmed;
        }
        return trimmed.substring(0, trimmed.offsetByCodePoints(0, maxCodePoints));
    }

    private static String pictureUrl(String value) {
        if (value == null || value.isBlank() || value.length() > 500) {
            return null;
        }
        String url = value.trim();
        if (!(url.startsWith("https://") || url.startsWith("http://"))) {
            return null;
        }
        return url;
    }

    private static BusinessException invalid() {
        return new BusinessException(AuthErrorCode.AUTH_GOOGLE_TOKEN_INVALID);
    }
}
