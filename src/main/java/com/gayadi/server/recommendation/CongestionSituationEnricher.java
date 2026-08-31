package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.model.TravelSituation;

import com.gayadi.server.congestion.CongestionForecastService;
import com.gayadi.server.congestion.dto.request.CongestionForecastRequest;
import com.gayadi.server.congestion.dto.response.CongestionForecastResponse;
import org.springframework.stereotype.Service;

/** 누락된 여행 혼잡 상황을 혼잡도 예측 결과로 보강합니다. */
@Service
public class CongestionSituationEnricher {

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
        CongestionForecastResponse forecast = congestion.forecast(
                new CongestionForecastRequest(
                        regionCode, districtCode, "", "", targetAt));
        TravelSituation.Congestion observed = new TravelSituation.Congestion(
                forecast.level(), null, forecast.area());
        return new TravelSituation(current.weather(), observed, current.transit());
    }
}
