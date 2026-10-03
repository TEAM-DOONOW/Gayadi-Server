package com.gayadi.server.route.dto.response;

import com.gayadi.server.route.TransportMode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** 거리·이동시간·체류시간을 모두 반영한 하루 추천 루트입니다. */
@Schema(name = "ItineraryRecommendationResponse", description = "엄격한 하루 여행루트")
public record ItineraryRecommendationResponse(
        @Schema(description = "여행루트 날짜(yyyy.MM.dd)", example = "2026.10.03")
        String date,
        @Schema(description = "하루 시작 시각", example = "10:00")
        String startTime,
        @Schema(description = "하루 종료 시각", example = "18:00")
        String endTime,
        @Schema(description = "이동수단", example = "PUBLIC_TRANSIT")
        TransportMode transportMode,
        @Schema(description = "루트 변형 번호", example = "0")
        int variation,
        @Schema(description = "이동시간이 직선거리 기반 추정치인지", example = "true")
        boolean estimated,
        @Schema(description = "구간 이동시간 합계(분)", example = "55")
        int totalTravelMinutes,
        @Schema(description = "체류시간 합계(분)", example = "320")
        int totalStayMinutes,
        @Schema(description = "한 줄 요약")
        String summary,
        @Schema(description = "방문 순서대로 정렬한 장소")
        List<ItineraryStopResponse> stops
) {
}
