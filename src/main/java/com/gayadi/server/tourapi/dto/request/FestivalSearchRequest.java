package com.gayadi.server.tourapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 기간 기반 축제·행사 검색 조건을 전달합니다. */
/** 행사·축제 검색 조건을 전달합니다. */
@Schema(name = "FestivalSearchRequest", description = "행사·축제 검색 조건")
public record FestivalSearchRequest(
        int pageSize,
        String cursor,
        String arrange,
        String eventStartDate,
        String eventEndDate,
        String modifiedtime,
        String lDongRegnCd,
        String lDongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3
) {
}
