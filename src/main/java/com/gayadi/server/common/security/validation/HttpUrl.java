package com.gayadi.server.common.security.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 외부 링크로 표시할 수 있는 HTTP·HTTPS 절대 URL 필드를 표시합니다. */
@Documented
@Constraint(validatedBy = HttpUrlValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpUrl {

    String message() default "{validation.security.http-url}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
