package com.gayadi.server.travel;

import com.gayadi.server.common.AppDateFormat;
import com.gayadi.server.config.ApiSuccessSchemas;
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
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips")
@Tag(name = "여행", description = "여행, 참여자와 진행 상태를 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "내 여행 목록")
    @ApiResponse(responseCode = "200", description = "현재 사용자가 참여한 여행 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiSuccessSchemas.Trip.class))))
    public List<Map<String, Object>> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listForUser(userId, status, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "여행 생성")
    @ApiResponse(responseCode = "201", description = "생성한 여행입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Trip.class)))
    public Map<String, Object> create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateTripRequest request) {
        return service.createForUser(
                userId, request.getName(), request.parsedStartDate(), request.parsedEndDate(), request.getCities());
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "여행 상세 조회")
    @ApiResponse(responseCode = "200", description = "여행의 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Trip.class)))
    public Map<String, Object> get(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        service.requireMember(tripId, userId);
        return service.view(tripId);
    }

    @PatchMapping("/{tripId}")
    @Operation(summary = "여행 정보 수정")
    @ApiResponse(responseCode = "200", description = "수정한 여행입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Trip.class)))
    public Map<String, Object> update(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody UpdateTripRequest request) {
        return service.update(userId, tripId, request.getName(), request.parsedStartDate(),
                request.parsedEndDate(), request.getCities(), request.getVersion());
    }

    @DeleteMapping("/{tripId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "여행 삭제")
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable long tripId) {
        service.delete(userId, tripId);
    }

    @PatchMapping("/{tripId}/status")
    @Operation(summary = "여행 진행 상태 변경")
    @ApiResponse(responseCode = "200", description = "상태를 바꾼 여행입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Trip.class)))
    public Map<String, Object> status(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody TripStatusRequest request) {
        return service.changeStatus(userId, tripId, request.getStatus());
    }

    @GetMapping("/{tripId}/participants")
    @Operation(summary = "여행 참여자 목록")
    @ApiResponse(responseCode = "200", description = "여행에 참여 중인 사용자 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiSuccessSchemas.Participant.class))))
    public List<Map<String, Object>> participants(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        service.requireMember(tripId, userId);
        return service.members(tripId);
    }

    @PutMapping("/{tripId}/participants/{participantUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "여행 참여자 추가")
    @ApiResponse(responseCode = "201", description = "추가한 여행 참여자입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.Participant.class)))
    public Map<String, Object> participant(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long participantUserId,
            @Valid @RequestBody(required = false) ParticipantRequest request) {
        ParticipantRequest settings = request == null ? new ParticipantRequest() : request;
        return service.addMemberAsOwner(userId, tripId, new TripService.AddMember(
                participantUserId, settings.getDeparturePlaceId(), settings.getReturnPlaceId()));
    }

    @DeleteMapping("/{tripId}/participants/{participantUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "여행 참여자 제외")
    public void participantDeletion(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @PathVariable long participantUserId) {
        service.removeMember(userId, tripId, participantUserId);
    }

    public static class CreateTripRequest {
        @NotBlank
        @Size(max = 100)
        private String name;
        @NotBlank
        @Pattern(regexp = AppDateFormat.DATE_PATTERN,
                message = "여행 날짜는 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.")
        private String startDate;
        @NotBlank
        @Pattern(regexp = AppDateFormat.DATE_PATTERN,
                message = "여행 날짜는 yyyy.MM.dd 또는 yyyy-MM-dd 형식이어야 합니다.")
        private String endDate;
        @NotEmpty
        @Size(max = 10)
        private List<@NotBlank @Size(max = 50) String> cities;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStartDate() { return startDate; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public String getEndDate() { return endDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
        public List<String> getCities() { return cities; }
        public void setCities(List<String> cities) { this.cities = cities; }

        LocalDate parsedStartDate() { return AppDateFormat.parseDate(startDate, "여행 날짜"); }
        LocalDate parsedEndDate() { return AppDateFormat.parseDate(endDate, "여행 날짜"); }
    }

    public static class UpdateTripRequest extends CreateTripRequest {
        @PositiveOrZero
        private Integer version;

        public Integer getVersion() { return version; }
        public void setVersion(Integer version) { this.version = version; }
    }

    public static class TripStatusRequest {
        @NotNull
        @Pattern(regexp = "PLANNING|ONGOING|IN_PROGRESS|COMPLETED",
                message = "여행 상태는 PLANNING, ONGOING, COMPLETED 중 하나여야 합니다.")
        private String status;

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }

    public static class ParticipantRequest {
        private Long departurePlaceId;
        private Long returnPlaceId;

        public Long getDeparturePlaceId() { return departurePlaceId; }
        public void setDeparturePlaceId(Long departurePlaceId) { this.departurePlaceId = departurePlaceId; }
        public Long getReturnPlaceId() { return returnPlaceId; }
        public void setReturnPlaceId(Long returnPlaceId) { this.returnPlaceId = returnPlaceId; }
    }
}
