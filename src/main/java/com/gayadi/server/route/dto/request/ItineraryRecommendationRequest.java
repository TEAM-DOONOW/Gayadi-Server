package com.gayadi.server.route.dto.request;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.route.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 하루 전체를 엄격하게 구성하는 여행루트 추천 요청입니다. */
@Schema(name = "ItineraryRecommendationRequest", description = "장소별 체류시간을 포함한 하루 여행루트 요청")
public record ItineraryRecommendationRequest(
        @Schema(description = "여행 기간 안의 날짜(yyyy.MM.dd 또는 yyyy-MM-dd)", example = "2026.10.03")
        @NotBlank
        @Pattern(regexp = AppDateFormat.DATE_PATTERN)
        String date,

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

        @Schema(description = "0이면 기본 루트, 값을 올리면 다른 전체 루트", example = "0")
        @Min(0)
        @Max(50)
        int variation,

        @Schema(description = "사용하지 않습니다. 확정은 PUT itinerary-selections/{date}입니다.", deprecated = true)
        @Size(max = 6)
        List<@Positive Long> expectedPlaceIds
) {
    public ItineraryRecommendationRequest {
        expectedPlaceIds = expectedPlaceIds == null ? List.of() : List.copyOf(expectedPlaceIds);
    }
}
