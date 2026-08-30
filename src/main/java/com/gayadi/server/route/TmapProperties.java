package com.gayadi.server.route;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "route.tmap")
public record TmapProperties(
        String appKey,
        @NotBlank String baseUrl,
        boolean fallbackToLocal,
        @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout,
        @Min(1) @Max(20) int maximumResults) {
}
