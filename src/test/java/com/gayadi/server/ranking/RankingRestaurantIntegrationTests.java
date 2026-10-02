package com.gayadi.server.ranking;

import com.gayadi.server.auth.UserService;
import com.gayadi.server.favorite.FavoritePlaceRepository;
import com.gayadi.server.ranking.dto.response.RankingItemResponse;
import com.gayadi.server.ranking.dto.response.RankingResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RankingRestaurantIntegrationTests {

    @Autowired UserService users;
    @Autowired FavoritePlaceRepository favorites;
    @Autowired RankingService rankings;
    @Autowired JdbcClient jdbc;

    @Test
    void ranksPublicRestaurantsByFavoriteCountWithinRegion() {
        long first = users.create("찜순위1").id();
        long second = users.create("찜순위2").id();
        long popular = restaurant("랭킹 인기 식당", "서울 중구 명동길 1", "RESTAURANT", "PUBLIC");
        long quiet = restaurant("랭킹 조용한 식당", "서울 종로구 1", "RESTAURANT", "PUBLIC");
        long cafe = restaurant("랭킹 카페", "서울 중구 2", "CAFE", "PUBLIC");
        long hidden = restaurant("랭킹 비공개 식당", "서울 중구 3", "RESTAURANT", "PRIVATE");
        favorites.upsert(first, popular, null);
        favorites.upsert(second, popular, null);
        favorites.upsert(first, quiet, null);
        favorites.upsert(first, cafe, null);
        favorites.upsert(second, cafe, null);
        favorites.upsert(first, hidden, null);

        RankingResponse response = rankings.rank(RankingType.RESTAURANT, "서울", 20);

        assertThat(response.source()).isEqualTo("GAYADI_FAVORITES");
        assertThat(response.items()).extracting(RankingItemResponse::title)
                .containsSubsequence("랭킹 인기 식당", "랭킹 조용한 식당")
                .doesNotContain("랭킹 카페", "랭킹 비공개 식당");
        RankingItemResponse top = response.items().stream()
                .filter(item -> item.placeId() == popular).findFirst().orElseThrow();
        assertThat(top.metric()).isEqualTo(2L);
        assertThat(top.metricLabel()).isEqualTo("찜 2개");
        assertThat(top.latitude()).isNotNull();

        assertThat(rankings.rank(RankingType.RESTAURANT, "부산", 20).items())
                .extracting(RankingItemResponse::title)
                .doesNotContain("랭킹 인기 식당");
    }

    private long restaurant(String name, String address, String category, String visibility) {
        jdbc.sql("""
                        INSERT INTO places (source, visibility, name, category, address, latitude, longitude, region_id)
                        VALUES ('USER', ?, ?, ?, ?, 37.56, 126.98, 1)
                        """)
                .params(visibility, name, category, address)
                .update();
        return jdbc.sql("SELECT MAX(id) FROM places").query(Long.class).single();
    }
}
