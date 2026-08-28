package com.gayadi.server.coordination;

import com.gayadi.server.common.AppDateFormat;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record DateAvailabilityRequest(
        @NotEmpty(message = "가능한 날짜를 하나 이상 선택해 주세요.")
        @Size(max = 366, message = "가능한 날짜는 366개까지 선택할 수 있습니다.")
        List<@Pattern(
                regexp = AppDateFormat.DATE_PATTERN,
                message = "가능한 날짜는 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.") String> dates
) {
}
