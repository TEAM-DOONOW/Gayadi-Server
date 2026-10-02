package com.gayadi.server.route.dto.request;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.route.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 미리 본 여행루트를 해당 날짜 일정으로 확정하는 요청입니다. */
@Schema(name = "ItinerarySelectionRequest", description = "미리 본 여행루트와 같은 조건과 장소 순서")
public record ItinerarySelectionRequest(
        @Schema(description = "하루 시작 시각(HH:mm)", example = "10:00")
        @NotBlank
        @Pattern(regexp = AppDateFormat.TIME_PATTERN)
        String startTime,

        @Schema(description = "하루 종료 시각(HH:mm). 시작보다 3~12시간 뒤", example = "18:00")
        @NotBlank
        @Pattern(regexp = AppDateFormat.TIME_PATTERN)
        String endTime,

        @Schema(description = "이동수단", example = "PUBLIC_TRANSIT")
        @NotNull
        TransportMode transportMode,

        @Schema(description = "미리보기에서 사용한 variation", example = "0")
        @Min(0)
        @Max(50)
        int variation,

        @Schema(description = "미리보기 응답 stops의 placeId 순서. 다시 계산한 결과와 다르면 409 ROUTE_CALCULATION_CHANGED",
                example = "[101, 205, 318]")
        @NotEmpty
        @Size(max = 6)
        List<@NotNull @Positive Long> expectedPlaceIds
) {
    public ItinerarySelectionRequest {
        expectedPlaceIds = expectedPlaceIds == null ? List.of() : List.copyOf(expectedPlaceIds);
    }
}
