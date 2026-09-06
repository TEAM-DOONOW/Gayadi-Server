package com.gayadi.server.common.security.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class SecurityValidationTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void httpUrlAllowsWebLinksAndRejectsExecutableOrCredentialUrls() {
        Assertions.assertThat(VALIDATOR.validate(new Example("https://example.com/posts/1")))
                .isEmpty();

        Assertions.assertThat(VALIDATOR.validate(new Example("javascript:alert(1)")))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("url");

        Assertions.assertThat(VALIDATOR.validate(new Example("file:///data/user/0/app/private.txt")))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("url");

        Assertions.assertThat(VALIDATOR.validate(new Example("content://contacts/people/1")))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("url");

        Assertions.assertThat(VALIDATOR.validate(new Example("/posts/1")))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("url");

        Assertions.assertThat(VALIDATOR.validate(new Example("https://user:pass@example.com")))
                .extracting(error -> error.getPropertyPath().toString())
                .contains("url");
    }

    private record Example(
            @HttpUrl
            String url
    ) {
    }
}
