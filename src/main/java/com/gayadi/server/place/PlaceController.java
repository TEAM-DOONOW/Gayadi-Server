package com.gayadi.server.place;

import org.springframework.web.bind.annotation.*;

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
    public List<Map<String, Object>> list(@RequestParam(required = false) String category) {
        return service.list(category);
    }

    @GetMapping("/{placeId}")
    public Map<String, Object> get(@PathVariable long placeId) {
        return service.get(placeId);
    }
}
