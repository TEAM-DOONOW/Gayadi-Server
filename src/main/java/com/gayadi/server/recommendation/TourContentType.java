package com.gayadi.server.recommendation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 추천 Agent가 허용하는 TourAPI 콘텐츠 타입의 분류와 실내 여부입니다. */
enum TourContentType {
    ATTRACTION("12", "ATTRACTION", false),
    CULTURAL_FACILITY("14", "CULTURE", true),
    FESTIVAL("15", "CULTURE", null),
    TRAVEL_COURSE("25", "ATTRACTION", false),
    LEISURE_SPORTS("28", "ETC", false),
    ACCOMMODATION("32", "ACCOMMODATION", true),
    SHOPPING("38", "SHOPPING", true),
    RESTAURANT("39", "RESTAURANT", true);

    private static final Map<String, TourContentType> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(TourContentType::code, Function.identity()));

    private final String code;
    private final String category;
    private final Boolean indoor;

    TourContentType(String code, String category, Boolean indoor) {
        this.code = code;
        this.category = category;
        this.indoor = indoor;
    }

    String code() { return code; }
    String category() { return category; }
    Boolean indoor() { return indoor; }

    static TourContentType fromCode(String code) {
        return BY_CODE.get(code);
    }

    static boolean supports(String code) {
        return BY_CODE.containsKey(code);
    }

    static List<String> generalRecommendationCodes() {
        return List.of(ATTRACTION.code, CULTURAL_FACILITY.code,
                SHOPPING.code, RESTAURANT.code);
    }

    static List<String> indoorRecommendationCodes() {
        return List.of(CULTURAL_FACILITY.code, SHOPPING.code, RESTAURANT.code);
    }
}
