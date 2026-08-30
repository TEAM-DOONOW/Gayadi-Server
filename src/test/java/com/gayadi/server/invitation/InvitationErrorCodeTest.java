package com.gayadi.server.invitation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        InvitationErrorCode[] values = InvitationErrorCode.values();

        assertThat(Arrays.stream(values).map(InvitationErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(InvitationErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(InvitationErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.invitation."));
    }
}
