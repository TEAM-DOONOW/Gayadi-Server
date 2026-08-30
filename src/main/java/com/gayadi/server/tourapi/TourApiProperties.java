package com.gayadi.server.tourapi;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "tour.api")
public record TourApiProperties(
        String key,
        @NotBlank String baseUrl,
        @NotBlank String mobileApp,
        @NotNull Duration connectTimeout,
        @NotNull Duration requestTimeout) {
}
