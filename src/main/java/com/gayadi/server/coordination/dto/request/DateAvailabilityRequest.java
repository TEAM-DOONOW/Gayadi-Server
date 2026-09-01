package com.gayadi.server.coordination.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

import com.gayadi.server.common.AppDateFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** DateAvailabilityRequest API 요청 데이터를 전달합니다. */
/** 참여 가능한 여행 날짜 범위를 전달합니다. */
@Schema(name = "DateAvailabilityRequest", description = "참여 가능한 여행 날짜 범위")
public record DateAvailabilityRequest(
        @NotEmpty(message = "{validation.coordination.dates.required}")
        @Size(max = 366, message = "{validation.coordination.dates.size}")
        List<@Pattern( regexp = AppDateFormat.DATE_PATTERN, message = "{validation.coordination.date.pattern}") String> dates
) {
}
