package com.gayadi.server.place;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/places")
public class PlaceController {
    private final PlaceService service;

    public PlaceController(PlaceService service) {
        this.service = service;
    }

    @GetMapping
    List<Map<String, Object>> list(@RequestParam(required = false) String category) {
        return service.list(category);
    }

    @GetMapping("/{placeId}")
    Map<String, Object> get(@PathVariable String placeId) {
        return service.get(placeId);
    }
}
