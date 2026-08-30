package com.gayadi.server.congestion;

import java.time.LocalDate;

public record CongestionForecast(
        String level,
        int concentrationScore,
        String area,
        String placeName,
        LocalDate targetDate,
        String source,
        boolean estimated,
        boolean providerDataAvailable,
        String confidence,
        String message
) {
}
