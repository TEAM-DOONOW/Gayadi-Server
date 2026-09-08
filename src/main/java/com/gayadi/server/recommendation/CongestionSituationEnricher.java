package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.model.TravelSituation;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.congestion.CongestionForecastService;
import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 누락된 여행 혼잡 상황을 혼잡도 예측 결과로 보강합니다. */
@Service
public class CongestionSituationEnricher {

    private static final Logger log = LoggerFactory.getLogger(CongestionSituationEnricher.class);

    private final CongestionForecastService congestion;

    public CongestionSituationEnricher(CongestionForecastService congestion) {
        this.congestion = congestion;
    }

    public TravelSituation enrich(
            TravelSituation situation, String regionCode, String districtCode, String targetAt) {
        TravelSituation current = situation == null ? TravelSituation.empty() : situation;
        if (!current.congestion().isEmpty()) {
            return current;
        }
        try {
            CongestionForecastResponse forecast = congestion.forecast(
                    new CongestionForecastRequest(
                            regionCode, districtCode, "", "", targetAt));
            TravelSituation.Congestion observed = new TravelSituation.Congestion(
                    forecast.level(), null, forecast.area());
            return new TravelSituation(current.weather(), observed, current.transit());
        } catch (BusinessException exception) {
            log.warn("혼잡 보강 생략: code={}", exception.getErrorCode().code());
            return current;
        }
    }
}
