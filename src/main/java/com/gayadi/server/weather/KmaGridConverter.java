package com.gayadi.server.weather;

/**
 * 기상청 단기예보 격자 좌표(X, Y) ↔ 위경도 변환.
 * 기상청 제공 C 예제(Lambert Conformal Conic Projection)를 Java로 포팅.
 * <p>
 * 단기예보서비스는 남한에 대해서만 제공되며, 격자 범위는 X[1..149], Y[1..253]이다.
 */
final class KmaGridConverter {

    private static final double RE = 6371.00877;
    private static final double GRID = 5.0;
    private static final double SLAT1 = 30.0;
    private static final double SLAT2 = 60.0;
    private static final double OLON = 126.0;
    private static final double OLAT = 38.0;
    private static final double XO = 210 / GRID;
    private static final double YO = 675 / GRID;

    static final int NX = 149;
    static final int NY = 253;

    private static final double PI = Math.asin(1.0) * 2.0;
    private static final double DEGRAD = PI / 180.0;
    private static final double RADDEG = 180.0 / PI;

    private static final double SN;
    private static final double SF;
    private static final double RO;
    private static final double RE_GRID;

    static {
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(PI * 0.25 + slat2 * 0.5) / Math.tan(PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        SN = sn;

        double sf = Math.tan(PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, SN) * Math.cos(slat1) / SN;
        SF = sf;

        double ro = Math.tan(PI * 0.25 + olat * 0.5);
        RE_GRID = RE / GRID;
        RO = RE_GRID * SF / Math.pow(ro, SN);
    }

    private KmaGridConverter() {
    }

    /**
     * 위경도를 격자 X, Y 좌표로 변환한다.
     *
     * @param lon 경도 (WGS84, 도 단위)
     * @param lat 위도 (WGS84, 도 단위)
     * @return 격자 좌표
     */
    static GridPoint toGrid(double lon, double lat) {
        double ra = Math.tan(PI * 0.25 + lat * DEGRAD * 0.5);
        ra = RE_GRID * SF / Math.pow(ra, SN);
        double theta = lon * DEGRAD - OLON * DEGRAD;
        if (theta > PI) theta -= 2.0 * PI;
        if (theta < -PI) theta += 2.0 * PI;
        theta *= SN;
        double x = ra * Math.sin(theta) + XO;
        double y = RO - ra * Math.cos(theta) + YO;
        return new GridPoint((int) (x + 1.5), (int) (y + 1.5));
    }

    /**
     * 격자 X, Y 좌표를 위경도로 변환한다.
     *
     * @param nx 격자 X 좌표
     * @param ny 격자 Y 좌표
     * @return 위경도
     */
    static LatLon toLatLon(int nx, int ny) {
        double xn = (nx - 1) - XO;
        double yn = RO - (ny - 1) + YO;
        double ra = Math.sqrt(xn * xn + yn * yn);
        if (SN < 0.0) ra = -ra;
        double alat = Math.pow(RE_GRID * SF / ra, 1.0 / SN);
        alat = 2.0 * Math.atan(alat) - PI * 0.5;

        double theta;
        if (xn == 0.0) {
            theta = 0.0;
        } else if (yn == 0.0) {
            theta = PI * 0.5;
            if (xn < 0.0) theta = -theta;
        } else {
            theta = Math.atan2(xn, yn);
        }
        double alon = theta / SN + OLON * DEGRAD;
        return new LatLon(alon * RADDEG, alat * RADDEG);
    }

    record GridPoint(int nx, int ny) {
    }

    record LatLon(double lon, double lat) {
    }
}
