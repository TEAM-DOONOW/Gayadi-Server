package com.gayadi.server.route.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/** 추천 루트의 장소별 방문·이동 계획입니다. */
@Schema(name = "ItineraryStopResponse", description = "여행루트의 한 방문 장소")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ItineraryStopResponse(
        @Schema(description = "1부터 시작하는 방문 순서", example = "1")
        int order,
        @Schema(description = "가야디 장소 번호", example = "101")
        long placeId,
        @Schema(description = "장소 이름", example = "경복궁")
        String name,
        @Schema(description = "장소 유형 표시명", example = "관광명소")
        String category,
        @Schema(description = "대표 이미지. 없으면 null")
        String imageUrl,
        @Schema(description = "위도", example = "37.5796")
        Double latitude,
        @Schema(description = "경도", example = "126.9770")
        Double longitude,
        @Schema(description = "도착 시각(HH:mm)", example = "10:00")
        String arrivalTime,
        @Schema(description = "출발 시각(HH:mm)", example = "11:20")
        String departureTime,
        @Schema(description = "체류시간(분)", example = "80")
        int stayMinutes,
        @Schema(description = "이전 장소에서 이동시간(분). 첫 장소는 0", example = "0")
        int travelMinutesFromPrevious,
        @Schema(description = "이전 장소에서 직선거리(m). 첫 장소는 0", example = "0")
        int distanceMetersFromPrevious
) {
}
