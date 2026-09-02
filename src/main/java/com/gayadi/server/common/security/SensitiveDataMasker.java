package com.gayadi.server.common.security;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 로그에 포함될 수 있는 대표적인 인증정보와 개인정보를 제거한다.
 *
 * <p>이 클래스는 최후 방어선이다. 비밀번호, 토큰, 요청 본문 같은 민감정보는
 * 애초에 로그 인자로 전달하지 않는 것이 우선이다.</p>
 */
public final class SensitiveDataMasker {

    private static final String REDACTED = "[REDACTED]";

    private static final Pattern SENSITIVE_KEY_VALUE = Pattern.compile(
            "(?i)([\"']?(?:password|passwd|pwd|access[_-]?token|refresh[_-]?token|id[_-]?token|"
                    + "token|authorization|"
                    + "cookie|set-cookie|api[_-]?key|service[_-]?key|client[_-]?secret|secret|"
                    + "resident[_-]?(?:registration[_-]?)?(?:number|no)|rrn|ssn)[\"']?\\s*[:=]\\s*[\"']?)"
                    + "([^\\s,;\"'&}]+)"
    );
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(Bearer\\s+)[A-Za-z0-9._~+/-]+=*");
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b([A-Z0-9._%+-])([A-Z0-9._%+-]*)(@)([A-Z0-9.-]+\\.[A-Z]{2,})\\b"
    );
    private static final Pattern RESIDENT_REGISTRATION_NUMBER = Pattern.compile(
            "(?<!\\d)(\\d{6})[- ]?([1-4]\\d{6})(?!\\d)"
    );
    private static final Pattern PHONE_NUMBER = Pattern.compile(
            "(?<!\\d)(01[016789])[- ]?(\\d{3,4})[- ]?(\\d{4})(?!\\d)"
    );
    private static final Pattern CARD_NUMBER = Pattern.compile(
            "(?<!\\d)(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})[- ]?(\\d{4})(?!\\d)"
    );
    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("[\\r\\n\\t]");

    private SensitiveDataMasker() {
    }

    public static String mask(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String masked = CONTROL_CHARACTERS.matcher(value).replaceAll(" ");
        masked = BEARER_TOKEN.matcher(masked).replaceAll("$1" + REDACTED);
        masked = SENSITIVE_KEY_VALUE.matcher(masked).replaceAll("$1" + REDACTED);
        masked = RESIDENT_REGISTRATION_NUMBER.matcher(masked).replaceAll("******-*******");
        masked = CARD_NUMBER.matcher(masked).replaceAll("$1-****-****-$4");
        masked = PHONE_NUMBER.matcher(masked).replaceAll("$1-****-$3");
        return maskEmails(masked);
    }

    private static String maskEmails(String value) {
        Matcher matcher = EMAIL.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + "***" + matcher.group(3) + matcher.group(4);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
