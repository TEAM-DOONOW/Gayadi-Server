package com.gayadi.server.event;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class EventErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        EventErrorCode[] values = EventErrorCode.values();

        assertThat(Arrays.stream(values).map(EventErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(EventErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(EventErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.event."));
    }
}
