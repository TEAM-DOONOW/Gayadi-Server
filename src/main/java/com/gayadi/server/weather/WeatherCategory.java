package com.gayadi.server.weather;

/**
 * 기상청 단기예보 카테고리 코드.
 * <p>
 * 초단기실황: T1H, RN1, UUU, VVV, REH, PTY, VEC, WSD
 * 초단기예보: T1H, RN1, SKY, UUU, VVV, REH, PTY, POP, LGT, VEC, WSD
 * 단기예보:   POP, PTY, PCP, REH, SNO, SKY, TMP, TMN, TMX, UUU, VVV, WAV, VEC, WSD
 */
enum WeatherCategory {

    T1H, RN1, UUU, VVV, REH, PTY, VEC, WSD,
    SKY, POP, LGT, PCP, SNO, TMP, TMN, TMX, WAV;
}
