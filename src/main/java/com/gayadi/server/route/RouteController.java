package com.gayadi.server.route;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/routes")
public class RouteController {
    private final RouteService service;

    public RouteController(RouteService service) {
        this.service = service;
    }

    @PostMapping("/recommend")
    Map<String, Object> recommend(@PathVariable String tripId,
                                  @RequestParam RouteService.RoutePhase phase,
                                  @RequestParam(required = false) String memberId) {
        return service.recommend(tripId, phase, memberId);
    }
}
