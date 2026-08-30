package com.gayadi.server.favorite;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FavoriteErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        FavoriteErrorCode[] values = FavoriteErrorCode.values();

        assertThat(Arrays.stream(values).map(FavoriteErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(FavoriteErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(FavoriteErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.favorite."));
        assertThat(Arrays.stream(values).map(FavoriteErrorCode::defaultMessage))
                .allMatch(message -> !message.isBlank());
    }
}
