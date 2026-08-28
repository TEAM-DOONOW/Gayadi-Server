package com.gayadi.server.recommendation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TravelSituationPolicyTest {

    @Test
    void rainMakesIndoorARequiredConstraint() {
        TravelSituation situation = new TravelSituation(
                new TravelSituation.Weather("RAIN", "", 12.0, 80, 24.0, 3.0),
                TravelSituation.Congestion.empty(),
                TravelSituation.Transit.empty());

        TravelSituation.Policy policy = situation.policy();

        assertThat(policy.indoorRequired()).isTrue();
        assertThat(policy.avoidOutdoor()).isTrue();
        assertThat(policy.avoidCrowded()).isFalse();
        assertThat(policy.transitDisrupted()).isFalse();
    }

    @Test
    void congestionAndMissedTransitBecomeIndependentSituationFlags() {
        TravelSituation situation = new TravelSituation(
                TravelSituation.Weather.empty(),
                new TravelSituation.Congestion("HIGH", 85, "울산 삼산동"),
                new TravelSituation.Transit(true, "BUS", 25, "버스 놓침", "2026-08-22T14:30:00+09:00"));

        TravelSituation.Policy policy = situation.policy();

        assertThat(policy.indoorRequired()).isFalse();
        assertThat(policy.avoidCrowded()).isTrue();
        assertThat(policy.transitDisrupted()).isTrue();
        assertThat(policy.summary()).contains("혼잡 장소 회피", "대중교통 대체 경로 필요");
    }
}
