package com.gayadi.server.tourapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

/** 키워드 기반 관광정보 검색 조건을 전달합니다. */
/** 관광지 키워드 검색 조건을 전달합니다. */
@Schema(name = "KeywordSearchRequest", description = "관광지 키워드 검색 조건")
public record KeywordSearchRequest(
        int pageSize,
        String cursor,
        String arrange,
        String keyword,
        String lDongRegnCd,
        String lDongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3
) {
}
