package com.gayadi.server.route;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/routes")
public class RouteController {

    private final RouteService service;

    public RouteController(RouteService service) {
        this.service = service;
    }

    @PostMapping("/recommend")
    public Map<String, Object> recommend(
            @PathVariable long tripId,
            @RequestParam RoutePhase phase,
            @RequestParam(required = false) Long memberId) {
        return service.recommend(tripId, phase, memberId);
    }
}
