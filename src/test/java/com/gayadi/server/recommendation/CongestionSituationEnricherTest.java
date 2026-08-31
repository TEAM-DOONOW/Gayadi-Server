package com.gayadi.server.recommendation;

import com.gayadi.server.recommendation.model.TravelSituation;

import com.gayadi.server.congestion.CongestionForecastService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class CongestionSituationEnricherTest {

    private final CongestionSituationEnricher enricher = new CongestionSituationEnricher(
            new CongestionForecastService(new ObjectMapper(), "", "https://example.invalid", "test"));

    @Test
    void fillsMissingCongestionWithClearlyIdentifiableEstimate() {
        TravelSituation result = enricher.enrich(TravelSituation.empty(),
                "11", "110", "2026-08-30T14:00:00+09:00");

        assertThat(result.congestion().level()).isEqualTo("CROWDED");
        assertThat(result.congestion().occupancyPercent()).isNull();
    }

    @Test
    void preservesUserSuppliedCongestion() {
        TravelSituation supplied = new TravelSituation(
                TravelSituation.Weather.empty(),
                new TravelSituation.Congestion("HIGH", 90, "광화문"),
                TravelSituation.Transit.empty());

        TravelSituation result = enricher.enrich(supplied,
                "11", "110", "2026-08-30T14:00:00+09:00");

        assertThat(result.congestion()).isEqualTo(supplied.congestion());
    }
}
