package com.gayadi.server.route;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "route.provider=tmap",
        "route.tmap.app-key=",
        "app.ai.enabled=false"
})
class TmapRouteProviderContextTests {

    @Autowired
    private RouteProvider routeProvider;

    @Autowired
    private TmapProperties properties;

    @Test
    void selectsTmapProviderAndBindsTypedConfiguration() {
        assertThat(routeProvider).isInstanceOf(TmapTransitRouteProvider.class);
        assertThat(properties.baseUrl()).isNotBlank();
        assertThat(properties.requestTimeout()).isPositive();
        assertThat(properties.maximumResults()).isBetween(1, 20);
    }
}
