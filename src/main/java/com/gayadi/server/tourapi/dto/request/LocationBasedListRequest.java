package com.gayadi.server.tourapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 좌표와 반경 기반 관광정보 목록 조회 조건을 전달합니다. */
/** 위치 기반 관광지 목록 조회 조건을 전달합니다. */
@Schema(name = "LocationBasedListRequest", description = "위치 기반 관광지 목록 조회 조건")
public record LocationBasedListRequest(
        int pageSize,
        String cursor,
        String arrange,
        String mapX,
        String mapY,
        String radius,
        String contentTypeId,
        String modifiedtime,
        String lDongRegnCd,
        String lDongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3
) {
}
