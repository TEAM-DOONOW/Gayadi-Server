package com.gayadi.server.weather;

/**
 * 기상청 단기예보 카테고리 코드.
 * <p>
 * 초단기실황: T1H, RN1, UUU, VVV, REH, PTY, VEC, WSD
 * 초단기예보: T1H, RN1, SKY, UUU, VVV, REH, PTY, POP, LGT, VEC, WSD
 * 단기예보:   POP, PTY, PCP, REH, SNO, SKY, TMP, TMN, TMX, UUU, VVV, WAV, VEC, WSD
 */
enum WeatherCategory {

    T1H("기온"),
    RN1("1시간 강수량"),
    UUU("동서바람성분"),
    VVV("남북바람성분"),
    REH("습도"),
    PTY("강수형태"),
    VEC("풍향"),
    WSD("풍속"),
    SKY("하늘상태"),
    POP("강수확률"),
    LGT("낙뢰"),
    PCP("1시간 강수량"),
    SNO("1시간 신적설"),
    TMP("1시간 기온"),
    TMN("일 최저기온"),
    TMX("일 최고기온"),
    WAV("파고");

    private final String description;

    WeatherCategory(String description) {
        this.description = description;
    }

    String description() {
        return description;
    }

    static WeatherCategory fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (WeatherCategory c : values()) {
            if (c.name().equals(code)) {
                return c;
            }
        }
        return null;
    }
}
