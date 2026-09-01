package com.gayadi.server.tourapi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/** TourAPI 관광 장소의 공통·소개 상세 필드를 반환합니다. */
/** 관광지 공통·소개 상세 정보를 반환합니다. */
@Schema(name = "TourPlaceDetailResponse", description = "관광지 공통·소개 상세 정보")
public record TourPlaceDetailResponse(
        String contentId,
        String contentTypeId,
        Map<String, String> common,
        Map<String, String> intro
) {
}
