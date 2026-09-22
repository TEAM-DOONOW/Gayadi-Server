package com.gayadi.server.place.dto.response;

import com.gayadi.server.route.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlaceTravelTimeResponse(
        TransportMode transportMode,
        @Schema(description = "이전 장소에서 후보까지 예상 이동시간(분)") int durationMinutes,
        @Schema(description = "후보에서 다음 장소까지 예상 이동시간(분). 다음 장소 없으면 null") Integer onwardDurationMinutes,
        @Schema(description = "경유 시 총 시간 - 이전→다음 직행 시간(분). 다음 장소 없으면 null") Long additionalDurationMinutes,
        String configuredProvider,
        @Schema(description = "일부 또는 전체 구간에 로컬 추정 사용 여부") boolean fallback
) {
}
