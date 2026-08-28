package com.gayadi.server.expense;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record SharedFundContributionRequest(
        @Min(1) @Max(1_000_000_000_000L) long amount
) {
}
