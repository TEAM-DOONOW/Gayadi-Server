package com.gayadi.server.tourapi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** TourAPI 관광 장소 목록과 다음 페이지 커서를 반환합니다. */
/** 관광지 목록과 페이지 정보를 반환합니다. */
@Schema(name = "TourListResponse", description = "관광지 목록과 페이지 정보")
public record TourListResponse(
        List<TourPlaceResponse> items,
        int totalCount,
        int pageSize,
        String nextCursor
) {
}
