package com.gayadi.server.favorite;

import com.gayadi.server.favorite.dto.request.FavoritePlaceSaveRequest;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FavoritePlaceSaveRequestValidationTest {

    @Autowired
    Validator validator;

    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void resolvesKoreanValidationMessage() {
        LocaleContextHolder.setLocale(Locale.KOREAN);

        assertThat(validator.validate(tooLongRequest()))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("메모는 최대 500자까지 입력할 수 있습니다."));
    }

    @Test
    void resolvesEnglishValidationMessage() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);

        assertThat(validator.validate(tooLongRequest()))
                .singleElement()
                .satisfies(violation -> assertThat(violation.getMessage())
                        .isEqualTo("The memo must be at most 500 characters."));
    }

    private FavoritePlaceSaveRequest tooLongRequest() {
        return new FavoritePlaceSaveRequest("a".repeat(501));
    }
}
