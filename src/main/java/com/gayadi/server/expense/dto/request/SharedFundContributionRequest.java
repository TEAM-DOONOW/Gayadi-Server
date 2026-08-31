package com.gayadi.server.expense.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** SharedFundContributionRequest API 요청 데이터를 전달합니다. */
/** 공동 경비에 추가할 분담금을 전달합니다. */
@Schema(name = "SharedFundContributionRequest", description = "공동 경비 분담금 추가 정보")
public record SharedFundContributionRequest(
        @Min(value = 1, message = "{validation.expense.contribution.amount.min}")
        @Max(value = 1_000_000_000_000L, message = "{validation.expense.contribution.amount.max}")
        long amount
) {
}
