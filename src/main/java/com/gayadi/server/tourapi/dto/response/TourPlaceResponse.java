package com.gayadi.server.tourapi.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** TourAPI 관광 장소의 기본·좌표·분류 정보를 반환합니다. */
/** TourAPI 관광지 기본 정보를 반환합니다. */
@Schema(name = "TourPlaceResponse", description = "TourAPI 관광지 기본 정보")
public record TourPlaceResponse(
        String contentId,
        String contentTypeId,
        String title,
        String address,
        String addressDetail,
        String zipcode,
        String tel,
        String firstImage,
        String firstImage2,
        String mapX,
        String mapY,
        String mapLevel,
        String createdTime,
        String modifiedTime,
        String copyrightType,
        String lDongRegnCd,
        String lDongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        String dist,
        String eventStartDate,
        String eventEndDate,
        String progressType,
        String festivalType
) {
}
