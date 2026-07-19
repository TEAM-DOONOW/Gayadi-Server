package com.gayadi.server.travel;

import com.gayadi.server.common.Location;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
    Map<String, Object> create(@Valid @RequestBody CreateTripRequest request) {
        return service.create(new TripService.CreateTrip(request.ownerId(), request.title(), request.departureMode(),
                request.departureAt(), request.meetingAt(), request.meetingLocation(),
                request.ownerDeparture(), request.ownerReturn()));
    }

    @GetMapping("/{tripId}")
    Map<String, Object> get(@PathVariable String tripId) {
        return service.get(tripId);
    }

    @GetMapping("/{tripId}/members")
    List<Map<String, Object>> members(@PathVariable String tripId) {
        return service.members(tripId);
    }

    @PostMapping("/{tripId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, Object> addMember(@PathVariable String tripId, @Valid @RequestBody AddMemberRequest request) {
        return service.addMember(tripId, new TripService.AddMember(
                request.userId(), request.departureLocation(), request.returnDestination()));
    }

    @PostMapping("/{tripId}/start")
    Map<String, Object> start(@PathVariable String tripId) {
        return service.start(tripId);
    }

    @PostMapping("/{tripId}/complete")
    Map<String, Object> complete(@PathVariable String tripId) {
        return service.complete(tripId);
    }

    public record CreateTripRequest(
            @NotBlank String ownerId,
            @NotBlank @Size(max = 120) String title,
            @NotNull DepartureMode departureMode,
            @NotNull @Future LocalDateTime departureAt,
            LocalDateTime meetingAt,
            @Valid Location meetingLocation,
            @NotNull @Valid Location ownerDeparture,
            @NotNull @Valid Location ownerReturn
    ) {
    }

    public record AddMemberRequest(
            @NotBlank String userId,
            @NotNull @Valid Location departureLocation,
            @NotNull @Valid Location returnDestination
    ) {
    }
}
