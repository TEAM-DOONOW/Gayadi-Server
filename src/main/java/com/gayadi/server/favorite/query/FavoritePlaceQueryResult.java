package com.gayadi.server.favorite.query;

import com.gayadi.server.place.model.PlaceCategory;

import java.time.LocalDateTime;

/** 찜 정보와 장소 정보를 함께 조회한 내부 전용 결과다. */
public record FavoritePlaceQueryResult(
        long id,
        String name,
        PlaceCategory category,
        String address,
        String roadAddress,
        Double latitude,
        Double longitude,
        Long regionId,
        String phone,
        String homepageUrl,
        String imageUrl,
        Boolean indoor,
        String description,
        String memo,
        LocalDateTime favoritedAt
) {
}
