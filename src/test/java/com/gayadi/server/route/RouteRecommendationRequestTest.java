package com.gayadi.server.route;

import com.gayadi.server.route.dto.request.RouteRecommendationRequest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteRecommendationRequestTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void defaultsExistingRequestsToTransitAndAcceptsExplicitMode() {
        assertThat(mapper.readValue("{\"type\":\"ITINERARY\"}", RouteRecommendationRequest.class)
                .transportMode()).isEqualTo(TransportMode.PUBLIC_TRANSIT);
        assertThat(mapper.readValue("{\"type\":\"ITINERARY\",\"transportMode\":\"CAR\"}",
                RouteRecommendationRequest.class).transportMode()).isEqualTo(TransportMode.CAR);
        assertThatThrownBy(() -> mapper.readValue("{\"type\":\"ITINERARY\",\"transportMode\":\"FLIGHT\"}",
                RouteRecommendationRequest.class)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void controllerPassesSelectedModeToService() {
        RouteService service = mock(RouteService.class);
        when(service.routePhase("ITINERARY")).thenReturn(RoutePhase.IN_TRIP);
        new RouteController(service).recommendation(7L, 3L,
                new RouteRecommendationRequest("ITINERARY", null, TransportMode.CAR));
        verify(service).recommendForUser(3L, 7L, RoutePhase.IN_TRIP, null, TransportMode.CAR);
    }
}
