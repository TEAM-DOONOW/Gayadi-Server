package com.gayadi.server.common.security.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/** URL이 사용자 정보 없는 HTTP·HTTPS 절대 주소인지 검사합니다. */
public class HttpUrlValidator implements ConstraintValidator<HttpUrl, CharSequence> {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return true;
        }
        try {
            URI uri = URI.create(value.toString());
            String scheme = uri.getScheme();
            return scheme != null
                    && ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))
                    && uri.isAbsolute()
                    && uri.getHost() != null
                    && uri.getUserInfo() == null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
