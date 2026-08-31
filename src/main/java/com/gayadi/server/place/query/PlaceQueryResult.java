package com.gayadi.server.place.query;

import com.gayadi.server.place.model.PlaceCategory;

import java.time.LocalDateTime;

/** 여행 장소 Repository의 PlaceQueryResult 조회 결과를 전달합니다. */
public record PlaceQueryResult(
        long id,
        String name,
        PlaceCategory category,
        String address,
        String roadAddress,
        Double latitude,
        Double longitude,
        long regionId,
        String regionName,
        String phone,
        String homepageUrl,
        String imageUrl,
        Boolean indoor,
        String basicInfo,
        String operatingHours,
        LocalDateTime updatedAt
) {
}
