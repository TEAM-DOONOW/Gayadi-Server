package com.gayadi.server.friendship;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class FriendshipErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        FriendshipErrorCode[] values = FriendshipErrorCode.values();

        assertThat(Arrays.stream(values).map(FriendshipErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(FriendshipErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(FriendshipErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.friendship."));
    }
}
