package com.gayadi.server.congestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "congestion.api")
public record CongestionApiProperties(
        String key,
        @NotBlank String baseUrl,
        @NotBlank String mobileApp,
        @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout,
        @NotNull Duration cacheTtl,
        @Min(1) @Max(10_000) int maximumCacheEntries) {
}
