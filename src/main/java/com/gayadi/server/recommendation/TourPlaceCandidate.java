package com.gayadi.server.recommendation;

/** TourAPI 또는 자체 장소 조회 결과를 Agent가 이해하는 공통 후보로 변환한 값입니다. */
public record TourPlaceCandidate(
        String placeId,
        String name,
        String category,
        String contentTypeId,
        String address,
        Double latitude,
        Double longitude,
        Boolean indoor,
        Double distanceKm,
        String description,
        String imageUrl
) {

    public TourPlaceCandidate {
        placeId = placeId == null ? "" : placeId;
        name = name == null ? "" : name;
        category = category == null ? "ETC" : category;
        contentTypeId = contentTypeId == null ? "" : contentTypeId;
        address = address == null ? "" : address;
        description = description == null ? "" : description;
        imageUrl = imageUrl == null ? "" : imageUrl;
    }

    public boolean matchesIndoorRequirement() {
        return Boolean.TRUE.equals(indoor);
    }
}
