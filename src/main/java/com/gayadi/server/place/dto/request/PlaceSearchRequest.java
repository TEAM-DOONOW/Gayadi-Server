package com.gayadi.server.place.dto.request;

import com.gayadi.server.place.model.PlaceSort;
import com.gayadi.server.route.TransportMode;
import com.gayadi.server.route.TransitPreference;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 장소찾기 필터와 일정에 추가할 위치의 좌표입니다. */
public record PlaceSearchRequest(
        @Size(max = 100) String query,
        @Size(max = 50) String region,
        String category,
        @Min(1) Long cursor,
        @Min(1) @Max(50) Integer limit,
        @Schema(description = "생략 시 RECENT. 이동시간 정렬은 TRAVEL_TIME") PlaceSort sort,
        TransportMode transportMode,
        @Schema(description = "이전 방문지 또는 숙소의 위도") Double originLatitude,
        @Schema(description = "이전 방문지 또는 숙소의 경도") Double originLongitude,
        @Schema(description = "일정 중간에 넣을 경우 다음 방문지 위도") Double nextLatitude,
        @Schema(description = "일정 중간에 넣을 경우 다음 방문지 경도") Double nextLongitude,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @Schema(description = "이전 장소에서 출발할 예정 시각. UTC 오프셋 필수. 생략 시 현재 시각",
                example = "2026-10-01T10:00:00+09:00") OffsetDateTime departureAt,
        @Schema(description = "대중교통 경로 선택 기준. 기본 FASTEST") TransitPreference transitPreference,
        @Min(0) @Max(1440)
        @Schema(description = "후보 장소 체류시간(분). 다음 구간 출발 시각에 반영. 기본 0") Integer visitDurationMinutes
) {
    public PlaceSearchRequest {
        if (limit == null) limit = 20;
        if (sort == null) sort = PlaceSort.RECENT;
        if (transportMode == null) transportMode = TransportMode.PUBLIC_TRANSIT;
        if (transitPreference == null) transitPreference = TransitPreference.FASTEST;
        if (visitDurationMinutes == null) visitDurationMinutes = 0;
    }
}
