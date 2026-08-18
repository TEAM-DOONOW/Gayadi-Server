package com.gayadi.server.event;

import com.gayadi.server.config.ApiSuccessSchemas;
import com.gayadi.server.travel.TripService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Tag(name = "현장 상황", description = "여행 중 확인된 상황과 일정 변경 제안을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class EventController {

    private final EventService service;
    private final TripService trips;

    public EventController(EventService service, TripService trips) {
        this.service = service;
        this.trips = trips;
    }

    @PostMapping("/event-observations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "현장 상황 등록")
    @ApiResponse(responseCode = "201", description = "상황 영향도 또는 일정 변경 제안입니다.",
            content = @Content(schema = @Schema(oneOf = {
                    ApiSuccessSchemas.EventObservation.class,
                    ApiSuccessSchemas.ChangeProposalDetail.class})))
    public Map<String, Object> observe(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody ObservationRequest request) {
        trips.requireMember(tripId, userId);
        return service.observe(tripId, new EventService.Observation(
                request.getPlaceId(),
                request.getEventType(),
                request.getSource(),
                request.getSeverity(),
                request.getValues()
        ));
    }

    @GetMapping("/change-proposals")
    @Operation(summary = "일정 변경 제안 목록")
    @ApiResponse(responseCode = "200", description = "일정 변경 제안 목록입니다.",
            content = @Content(array = @ArraySchema(schema = @Schema(
                    implementation = ApiSuccessSchemas.ChangeProposalDetail.class))))
    public List<Map<String, Object>> proposals(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        trips.requireMember(tripId, userId);
        return service.proposals(tripId, limit, offset);
    }

    @PatchMapping("/change-proposals/{proposalId}")
    @Operation(summary = "일정 변경 제안 처리")
    @ApiResponse(responseCode = "200", description = "처리한 일정 변경 제안입니다.",
            content = @Content(schema = @Schema(
                    implementation = ApiSuccessSchemas.ChangeProposalDetail.class)))
    public Map<String, Object> decide(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long proposalId,
            @Valid @RequestBody DecisionRequest request) {
        trips.requireMember(tripId, userId);
        return service.decide(tripId, proposalId, new EventService.Decision(
                request.getApprove(),
                request.getSelectedOptionKey(),
                request.getBaseRevisionNo(),
                userId
        ));
    }

    public static class ObservationRequest {
        private Long placeId;
        @NotBlank
        @Pattern(regexp = "WEATHER|CONGESTION|TRANSPORT|CLOSURE|DISASTER",
                message = "현장 상황 종류가 올바르지 않습니다.")
        private String eventType;
        @NotBlank
        @Size(max = 50)
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
        @NotNull
        private Boolean approve;
        private String selectedOptionKey;
        @NotNull
        @PositiveOrZero
        private Integer baseRevisionNo;

        public Boolean getApprove() { return approve; }
        public void setApprove(Boolean approve) { this.approve = approve; }
        public String getSelectedOptionKey() { return selectedOptionKey; }
        public void setSelectedOptionKey(String selectedOptionKey) { this.selectedOptionKey = selectedOptionKey; }
        public Integer getBaseRevisionNo() { return baseRevisionNo; }
        public void setBaseRevisionNo(Integer baseRevisionNo) { this.baseRevisionNo = baseRevisionNo; }
    }
}
