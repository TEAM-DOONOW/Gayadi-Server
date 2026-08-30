package com.gayadi.server.recommendation;

import com.gayadi.server.congestion.CongestionForecast;
import com.gayadi.server.congestion.CongestionForecastService;
import org.springframework.stereotype.Service;

@Service
public class CongestionSituationEnricher {

    private final CongestionForecastService congestion;

    public CongestionSituationEnricher(CongestionForecastService congestion) {
        this.congestion = congestion;
    }

    public TravelSituation enrich(
            TravelSituation situation, String regionCode, String districtCode, String targetAt) {
        TravelSituation current = situation == null ? TravelSituation.empty() : situation;
        if (!current.congestion().isEmpty()) return current;
        CongestionForecast forecast = congestion.forecast(
                new CongestionForecastService.Request(
                        regionCode, districtCode, "", "", targetAt));
        TravelSituation.Congestion observed = new TravelSituation.Congestion(
                forecast.level(), null, forecast.area());
        return new TravelSituation(current.weather(), observed, current.transit());
    }
}
