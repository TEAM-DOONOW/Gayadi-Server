package com.gayadi.server.place.dto.response;

import com.gayadi.server.route.TransportMode;
import com.gayadi.server.route.TransitPreference;
import java.time.OffsetDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

public record PlaceTravelTimeResponse(
        TransportMode transportMode,
        @Schema(description = "이전 장소에서 후보까지 예상 이동시간(분)") int durationMinutes,
        @Schema(description = "후보에서 다음 장소까지 예상 이동시간(분). 다음 장소 없으면 null") Integer onwardDurationMinutes,
        @Schema(description = "경유 시 총 시간 - 이전→다음 직행 시간(분). 다음 장소 없으면 null") Long additionalDurationMinutes,
        String configuredProvider,
        @Schema(description = "일부 또는 전체 구간에 로컬 추정 사용 여부") boolean fallback,
        @Schema(description = "이전 장소에서 출발할 기준 시각") OffsetDateTime departureAt,
        @Schema(description = "후보 도착 후 체류시간을 더한 다음 구간 출발 시각") OffsetDateTime onwardDepartureAt,
        @Schema(description = "선택된 경로 구간의 총 환승 횟수") int transferCount,
        TransitPreference transitPreference,
        @Schema(description = "지정 시각의 운행 여부를 확인했는지. 미래 소요시간 정확성 보장 아님") boolean scheduledTimeApplied
) {
}
