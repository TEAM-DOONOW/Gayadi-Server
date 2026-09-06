package com.gayadi.server.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** 브라우저 클라이언트에 허용할 교차 출처 요청 범위를 정의합니다. */
@ConfigurationProperties(prefix = "app.security.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null
                ? List.of()
                : allowedOrigins.stream()
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .distinct()
                        .toList();
    }
}
