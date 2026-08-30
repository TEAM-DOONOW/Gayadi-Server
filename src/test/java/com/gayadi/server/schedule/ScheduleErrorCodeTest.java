package com.gayadi.server.schedule;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        ScheduleErrorCode[] values = ScheduleErrorCode.values();

        assertThat(Arrays.stream(values).map(ScheduleErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(ScheduleErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(ScheduleErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.schedule."));
        assertThat(Arrays.stream(values).map(ScheduleErrorCode::defaultMessage))
                .allMatch(message -> !message.isBlank());
    }
}
