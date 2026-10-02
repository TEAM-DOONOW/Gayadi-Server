package com.gayadi.server.place;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceRepositoryRegionTest {

    @Test
    void splitsCompositeRegionIntoCityTokensForTheRouteQuery() {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        PlaceRepository.appendItineraryRegion(sql, parameters, "수원·용인");

        assertThat(sql.toString()).contains("r.name = ?");
        assertThat(parameters).containsExactly(
                "수원", "수원%", "%수원%",
                "용인", "용인%", "%용인%");
    }

    @Test
    void keepsASingleCityAsOneToken() {
        assertThat(PlaceRepository.itineraryRegionTokens("강릉·속초"))
                .containsExactly("강릉", "속초");
        assertThat(PlaceRepository.itineraryRegionTokens("서울"))
                .containsExactly("서울");
        assertThat(PlaceRepository.itineraryRegionTokens("제주 성산"))
                .containsExactly("제주", "성산");
    }
}
