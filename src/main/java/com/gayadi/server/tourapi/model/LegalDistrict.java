package com.gayadi.server.tourapi.model;

/** TourAPI가 사용하는 법정동 코드와 이름입니다. */
public record LegalDistrict(
        String code,
        String name
) {
}
