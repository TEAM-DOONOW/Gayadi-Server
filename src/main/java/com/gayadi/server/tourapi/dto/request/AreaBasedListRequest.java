package com.gayadi.server.tourapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 지역 기반 관광정보 목록 조회 조건을 전달합니다. */
/** 지역 기반 관광지 목록 조회 조건을 전달합니다. */
@Schema(name = "AreaBasedListRequest", description = "지역 기반 관광지 목록 조회 조건")
public record AreaBasedListRequest(
        int pageSize,
        String cursor,
        String arrange,
        String contentTypeId,
        String lDongRegnCd,
        String lDongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3
) {
}
