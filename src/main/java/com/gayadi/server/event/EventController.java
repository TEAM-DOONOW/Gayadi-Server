package com.gayadi.server.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
    public Map<String, Object> observe(
            @PathVariable long tripId,
            @Valid @RequestBody ObservationRequest request) {
        return service.observe(tripId, new EventService.Observation(
                request.getPlaceId(),
                request.getEventType(),
                request.getSource(),
                request.getSeverity(),
                request.getValues()
        ));
    }

    @GetMapping("/change-proposals")
    public List<Map<String, Object>> proposals(@PathVariable long tripId) {
        return service.proposals(tripId);
    }

    @PostMapping("/change-proposals/{proposalId}/decision")
    public Map<String, Object> decide(
            @PathVariable long tripId,
            @PathVariable long proposalId,
            @Valid @RequestBody DecisionRequest request) {
        return service.decide(tripId, proposalId, new EventService.Decision(
                request.isApprove(),
                request.getSelectedOptionKey(),
                request.getBaseRevisionNo(),
                request.getDecidedBy()
        ));
    }

    public static class ObservationRequest {
        private Long placeId;
        @NotBlank
        private String eventType;
        @NotBlank
        private String source;
        @NotNull
        private Severity severity;
        @NotEmpty
        private Map<String, Object> values;

        public Long getPlaceId() { return placeId; }
        public void setPlaceId(Long placeId) { this.placeId = placeId; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public Severity getSeverity() { return severity; }
        public void setSeverity(Severity severity) { this.severity = severity; }
        public Map<String, Object> getValues() { return values; }
        public void setValues(Map<String, Object> values) { this.values = values; }
    }

    public static class DecisionRequest {
        private boolean approve;
        private String selectedOptionKey;
        @Positive
        private int baseRevisionNo;
        @NotNull
        private Long decidedBy;

        public boolean isApprove() { return approve; }
        public void setApprove(boolean approve) { this.approve = approve; }
        public String getSelectedOptionKey() { return selectedOptionKey; }
        public void setSelectedOptionKey(String selectedOptionKey) { this.selectedOptionKey = selectedOptionKey; }
        public int getBaseRevisionNo() { return baseRevisionNo; }
        public void setBaseRevisionNo(int baseRevisionNo) { this.baseRevisionNo = baseRevisionNo; }
        public Long getDecidedBy() { return decidedBy; }
        public void setDecidedBy(Long decidedBy) { this.decidedBy = decidedBy; }
    }
}
