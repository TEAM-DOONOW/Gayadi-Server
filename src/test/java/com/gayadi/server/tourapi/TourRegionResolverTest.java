package com.gayadi.server.tourapi;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TourRegionResolverTest {

    @Test
    void resolvesCompositeAppRegionAndInterleavesItsCities() {
        TourRegionResolver resolver = new TourRegionResolver(new StubTourApiService());

        List<TourRegionResolver.RegionCode> result = resolver.resolve("수원·용인");

        assertThat(result).extracting(TourRegionResolver.RegionCode::districtCode)
                .containsExactly("110", "460", "111", "461");
    }

    @Test
    void keepsMetropolitanRegionWideInsteadOfGuessingOneDistrict() {
        TourRegionResolver resolver = new TourRegionResolver(new StubTourApiService());

        assertThat(resolver.resolve("서울"))
                .containsExactly(new TourRegionResolver.RegionCode("11", "", "서울"));
    }

    @Test
    void acceptsTheAndroidDefaultJejuSeongsanAlias() {
        TourRegionResolver resolver = new TourRegionResolver(new StubTourApiService());

        assertThat(resolver.resolve("제주 성산"))
                .containsExactly(new TourRegionResolver.RegionCode("50", "", "제주"));
    }

    private static final class StubTourApiService extends TourApiService {
        private StubTourApiService() {
            super(new ObjectMapper(), "test", "http://example.com", "test");
        }

        @Override
        public List<LegalDistrict> legalDistricts(String regionCode) {
            return List.of(
                    new LegalDistrict("41110", "수원시 장안구"),
                    new LegalDistrict("41111", "수원시 권선구"),
                    new LegalDistrict("41460", "용인시 처인구"),
                    new LegalDistrict("41461", "용인시 기흥구"));
        }
    }
}
