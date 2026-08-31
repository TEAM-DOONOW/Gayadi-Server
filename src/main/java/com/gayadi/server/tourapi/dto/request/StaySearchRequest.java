package com.gayadi.server.tourapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 숙박 관광정보 검색 조건을 전달합니다. */
/** 숙박 시설 검색 조건을 전달합니다. */
@Schema(name = "StaySearchRequest", description = "숙박 시설 검색 조건")
public record StaySearchRequest(
        int pageSize,
        String cursor,
        String arrange,
        String modifiedtime,
        String lDongRegnCd,
        String lDongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3
) {
}
