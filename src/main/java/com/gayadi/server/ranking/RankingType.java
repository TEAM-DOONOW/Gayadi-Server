package com.gayadi.server.ranking;

/** 홈 화면 카테고리별 순위 종류를 정의합니다. */
public enum RankingType {
    /** 한국관광 데이터랩 중심 관광지 방문 순위 */
    ATTRACTION,
    /** 진행 중이거나 곧 시작하는 축제·행사 */
    FESTIVAL,
    /** 한국관광 데이터랩 기초지자체 방문자 수 순위 */
    REGION,
    /** 가야디 사용자 찜 수 기준 맛집 순위 */
    RESTAURANT
}
