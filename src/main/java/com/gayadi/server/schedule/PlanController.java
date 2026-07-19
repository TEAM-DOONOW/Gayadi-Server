package com.gayadi.server.schedule;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    Map<String, Object> generate(@PathVariable String tripId) {
        return service.generate(tripId);
    }

    @GetMapping
    Map<String, Object> get(@PathVariable String tripId) {
        return service.get(tripId);
    }
}
