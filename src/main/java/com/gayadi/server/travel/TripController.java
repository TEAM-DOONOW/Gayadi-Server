package com.gayadi.server.travel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody CreateTripRequest request) {
        return service.create(new TripService.CreateTrip(
                request.getOwnerId(),
                request.getTitle(),
                request.getStartDate(),
                request.getEndDate(),
                request.getDepartureMode(),
                request.getMeetingAt(),
                request.getMeetingPlaceId(),
                request.getRegionId(),
                request.getTripPreferences(),
                request.getMaxMembers(),
                request.getOwnerDeparturePlaceId(),
                request.getOwnerReturnPlaceId()
        ));
    }

    @GetMapping("/{tripId}")
    public Map<String, Object> get(@PathVariable long tripId) {
        return service.get(tripId);
    }

    @GetMapping("/{tripId}/members")
    public List<Map<String, Object>> members(@PathVariable long tripId) {
        return service.members(tripId);
    }

    @PostMapping("/{tripId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addMember(
            @PathVariable long tripId,
            @Valid @RequestBody AddMemberRequest request) {
        return service.addMember(tripId, new TripService.AddMember(
                request.getUserId(),
                request.getDeparturePlaceId(),
                request.getReturnPlaceId()
        ));
    }

    @PostMapping("/{tripId}/start")
    public Map<String, Object> start(@PathVariable long tripId) {
        return service.start(tripId);
    }

    @PostMapping("/{tripId}/complete")
    public Map<String, Object> complete(@PathVariable long tripId) {
        return service.complete(tripId);
    }

    public static class CreateTripRequest {
        @NotNull
        private Long ownerId;
        @NotBlank
        @Size(max = 100)
        private String title;
        @NotNull
        private LocalDate startDate;
        @NotNull
        private LocalDate endDate;
        @NotNull
        private DepartureMode departureMode;
        private LocalDateTime meetingAt;
        private Long meetingPlaceId;
        @NotNull
        private Long regionId;
        private String tripPreferences;
        private Integer maxMembers;
        private Long ownerDeparturePlaceId;
        private Long ownerReturnPlaceId;

        public Long getOwnerId() { return ownerId; }
        public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
        public DepartureMode getDepartureMode() { return departureMode; }
        public void setDepartureMode(DepartureMode departureMode) { this.departureMode = departureMode; }
        public LocalDateTime getMeetingAt() { return meetingAt; }
        public void setMeetingAt(LocalDateTime meetingAt) { this.meetingAt = meetingAt; }
        public Long getMeetingPlaceId() { return meetingPlaceId; }
        public void setMeetingPlaceId(Long meetingPlaceId) { this.meetingPlaceId = meetingPlaceId; }
        public Long getRegionId() { return regionId; }
        public void setRegionId(Long regionId) { this.regionId = regionId; }
        public String getTripPreferences() { return tripPreferences; }
        public void setTripPreferences(String tripPreferences) { this.tripPreferences = tripPreferences; }
        public Integer getMaxMembers() { return maxMembers; }
        public void setMaxMembers(Integer maxMembers) { this.maxMembers = maxMembers; }
        public Long getOwnerDeparturePlaceId() { return ownerDeparturePlaceId; }
        public void setOwnerDeparturePlaceId(Long ownerDeparturePlaceId) { this.ownerDeparturePlaceId = ownerDeparturePlaceId; }
        public Long getOwnerReturnPlaceId() { return ownerReturnPlaceId; }
        public void setOwnerReturnPlaceId(Long ownerReturnPlaceId) { this.ownerReturnPlaceId = ownerReturnPlaceId; }
    }

    public static class AddMemberRequest {
        @NotNull
        private Long userId;
        private Long departurePlaceId;
        private Long returnPlaceId;

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public Long getDeparturePlaceId() { return departurePlaceId; }
        public void setDeparturePlaceId(Long departurePlaceId) { this.departurePlaceId = departurePlaceId; }
        public Long getReturnPlaceId() { return returnPlaceId; }
        public void setReturnPlaceId(Long returnPlaceId) { this.returnPlaceId = returnPlaceId; }
    }
}
