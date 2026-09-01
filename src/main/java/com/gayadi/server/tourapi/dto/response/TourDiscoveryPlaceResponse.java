package com.gayadi.server.tourapi.dto.response;

import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** 관광 장소 기본 정보와 해당 날짜의 혼잡도 예측을 반환합니다. */
@Schema(description = "관광정보와 관광지 집중률 기반 혼잡도 예측을 합친 장소")
public record TourDiscoveryPlaceResponse(
        String contentId,
        String contentTypeId,
        String title,
        String address,
        String addressDetail,
        String firstImage,
        String mapX,
        String mapY,
        String lDongRegnCd,
        String lDongSignguCd,
        String lclsSystm1,
        String lclsSystm2,
        String lclsSystm3,
        String crowdLevel,
        int concentrationScore,
        String crowdSource,
        boolean crowdEstimated,
        boolean crowdProviderDataAvailable,
        String crowdConfidence,
        String crowdMessage,
        LocalDate crowdTargetDate
) {

    public static TourDiscoveryPlaceResponse of(
            TourPlaceResponse place,
            CongestionForecastResponse forecast) {
        return new TourDiscoveryPlaceResponse(
                place.contentId(),
                place.contentTypeId(),
                place.title(),
                place.address(),
                place.addressDetail(),
                place.firstImage(),
                place.mapX(),
                place.mapY(),
                place.lDongRegnCd(),
                place.lDongSignguCd(),
                place.lclsSystm1(),
                place.lclsSystm2(),
                place.lclsSystm3(),
                forecast.level(),
                forecast.concentrationScore(),
                forecast.source(),
                forecast.estimated(),
                forecast.providerDataAvailable(),
                forecast.confidence(),
                forecast.message(),
                forecast.targetDate());
    }
}
