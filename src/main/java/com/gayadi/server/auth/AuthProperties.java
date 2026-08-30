package com.gayadi.server.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        @Min(1) @Max(20) int maximumFailedAttempts,
        @Min(1) @Max(1440) int lockMinutes) {
}
