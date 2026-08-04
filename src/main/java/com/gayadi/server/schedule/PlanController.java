package com.gayadi.server.schedule;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/plan")
public class PlanController {

    private final PlanService service;

    public PlanController(PlanService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> generate(@PathVariable long tripId) {
        return service.generate(tripId);
    }

    @GetMapping
    public Map<String, Object> get(@PathVariable long tripId) {
        return service.get(tripId);
    }
}
