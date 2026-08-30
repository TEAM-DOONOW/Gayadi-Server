package com.gayadi.server.travel;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TripErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        TripErrorCode[] values = TripErrorCode.values();

        assertThat(Arrays.stream(values).map(TripErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(TripErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(TripErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.trip."));
        assertThat(Arrays.stream(values).map(TripErrorCode::defaultMessage))
                .allMatch(message -> !message.isBlank());
    }
}
