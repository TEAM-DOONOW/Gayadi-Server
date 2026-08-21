package com.gayadi.server.weather;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class KmaGridConverterTest {

    /**
     * 기상청 가이드 xlsx에 기재된 서울특별시 격자 좌표.
     * lon=126.980008, lat=37.563569 → nx=60, ny=127
     */
    @Test
    void convertsSeoulCityHallToGrid60x127() {
        KmaGridConverter.GridPoint gp = KmaGridConverter.toGrid(126.980008, 37.563569);
        Assertions.assertThat(gp.nx()).isEqualTo(60);
        Assertions.assertThat(gp.ny()).isEqualTo(127);
    }

    /**
     * 기상청 가이드 xlsx — 종로구 창신제1동.
     * lon=127.018464, lat=37.567936 → nx=61, ny=127
     */
    @Test
    void convertsChangsinToGrid61x127() {
        KmaGridConverter.GridPoint gp = KmaGridConverter.toGrid(127.018464, 37.567936);
        Assertions.assertThat(gp.nx()).isEqualTo(61);
        Assertions.assertThat(gp.ny()).isEqualTo(127);
    }

    /**
     * 기상청 가이드 xlsx — 종로구 숭인제2동.
     * lon=127.022056, lat=37.572036 → nx=61, ny=127
     */
    @Test
    void convertsSuginToGrid61x127() {
        KmaGridConverter.GridPoint gp = KmaGridConverter.toGrid(127.022056, 37.572036);
        Assertions.assertThat(gp.nx()).isEqualTo(61);
        Assertions.assertThat(gp.ny()).isEqualTo(127);
    }

    /**
     * 기상청 C 예제 역방향 검증 — 격자(59, 125)를 위경도로 변환.
     * 예제 출력: lon ≈ 126.9298, lat ≈ 37.4882
     */
    @Test
    void convertsGrid59x125ToExpectedLatLon() {
        KmaGridConverter.LatLon ll = KmaGridConverter.toLatLon(59, 125);
        Assertions.assertThat(ll.lon()).isCloseTo(126.9298, Assertions.within(0.01));
        Assertions.assertThat(ll.lat()).isCloseTo(37.4882, Assertions.within(0.01));
    }

    /**
     * 왕복 검증 — 위경도 → 격자 → 위경도 변환 시 원래 값에 근사.
     * 격자 해상도 5km이므로 허용 오차를 0.02도(약 2km)로 설정.
     */
    @Test
    void roundTripPreservesCoordinates() {
        double lon = 126.98;
        double lat = 37.56;
        KmaGridConverter.GridPoint gp = KmaGridConverter.toGrid(lon, lat);
        KmaGridConverter.LatLon ll = KmaGridConverter.toLatLon(gp.nx(), gp.ny());
        Assertions.assertThat(ll.lon()).isCloseTo(lon, Assertions.within(0.02));
        Assertions.assertThat(ll.lat()).isCloseTo(lat, Assertions.within(0.02));
    }
}
