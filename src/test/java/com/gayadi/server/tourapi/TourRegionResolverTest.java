package com.gayadi.server.tourapi;

import com.gayadi.server.tourapi.model.LegalDistrict;

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

    @Test
    void resolvesASingleCityInsideACompositeRegion() {
        TourRegionResolver resolver = new TourRegionResolver(new StubTourApiService());

        assertThat(resolver.resolve("수원")).extracting(TourRegionResolver.RegionCode::districtCode)
                .containsExactly("110", "111");
        assertThat(resolver.resolve("용인")).extracting(TourRegionResolver.RegionCode::districtCode)
                .containsExactly("460", "461");
    }

    @Test
    void resolvesAMetropolitanCityByItsOwnName() {
        TourRegionResolver resolver = new TourRegionResolver(new StubTourApiService());

        assertThat(resolver.resolve("광주"))
                .containsExactly(new TourRegionResolver.RegionCode("29", "", "광주"));
        assertThat(resolver.resolve("부산"))
                .containsExactly(new TourRegionResolver.RegionCode("26", "", "부산"));
    }

    @Test
    void resolvesDistrictCodeFromAPlaceAddress() {
        TourRegionResolver resolver = new TourRegionResolver(new StubTourApiService());

        assertThat(resolver.resolveAddress("경기도 수원시 장안구 영화동 320-2"))
                .contains(new TourRegionResolver.RegionCode("41", "110", "수원시 장안구"));
        assertThat(resolver.resolveAddress("경기 용인시 기흥구 보정동"))
                .contains(new TourRegionResolver.RegionCode("41", "461", "용인시 기흥구"));
        assertThat(resolver.resolveAddress("미국 뉴욕")).isEmpty();
        assertThat(resolver.resolveAddress("")).isEmpty();
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
