package com.gayadi.server.expense;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseErrorCodeTest {

    @Test
    void exposesUniqueStableCodesWithCompleteMetadata() {
        ExpenseErrorCode[] values = ExpenseErrorCode.values();

        assertThat(Arrays.stream(values).map(ExpenseErrorCode::code))
                .doesNotHaveDuplicates()
                .allMatch(code -> code.matches("[A-Z][A-Z0-9_]*"));
        assertThat(Arrays.stream(values).map(ExpenseErrorCode::status)).doesNotContainNull();
        assertThat(Arrays.stream(values).map(ExpenseErrorCode::messageKey))
                .allMatch(key -> key.startsWith("error.expense."));
        assertThat(Arrays.stream(values).map(ExpenseErrorCode::defaultMessage))
                .allMatch(message -> !message.isBlank());
    }
}
