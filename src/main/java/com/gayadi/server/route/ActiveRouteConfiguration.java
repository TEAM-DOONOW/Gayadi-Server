package com.gayadi.server.route;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ActiveRouteConfiguration {
    @Bean
    LocalActiveRouteProvider walkingRouteProvider() {
        return new LocalActiveRouteProvider(TransportMode.WALK);
    }

    @Bean
    LocalActiveRouteProvider bicycleRouteProvider() {
        return new LocalActiveRouteProvider(TransportMode.BICYCLE);
    }
}
