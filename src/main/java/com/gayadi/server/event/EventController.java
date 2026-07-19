package com.gayadi.server.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
public class EventController {
    private final EventService service;

    public EventController(EventService service) {
        this.service = service;
    }

    @PostMapping("/event-observations")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> observe(@PathVariable String tripId, @Valid @RequestBody ObservationRequest request) {
        return service.observe(tripId, new EventService.Observation(request.placeId(), request.eventType(),
                request.source(), request.severity(), request.values()));
    }

    @GetMapping("/change-proposals")
    List<Map<String, Object>> proposals(@PathVariable String tripId) {
        return service.proposals(tripId);
    }

    @PostMapping("/change-proposals/{proposalId}/decision")
    Map<String, Object> decide(@PathVariable String tripId, @PathVariable String proposalId,
                               @Valid @RequestBody DecisionRequest request) {
        return service.decide(tripId, proposalId, new EventService.Decision(request.approve(),
                request.selectedOptionKey(), request.baseRevisionNo(), request.decidedBy()));
    }

    public record ObservationRequest(@NotBlank String placeId, @NotBlank String eventType,
                                     @NotBlank String source, @NotNull EventService.Severity severity,
                                     @NotEmpty Map<String, Object> values) {
    }

    public record DecisionRequest(boolean approve, String selectedOptionKey,
                                  @Positive int baseRevisionNo, @NotBlank String decidedBy) {
    }
}
