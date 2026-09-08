package com.gayadi.server.travel;

import com.gayadi.server.travel.dto.request.CreateTripRequest;
import com.gayadi.server.travel.dto.request.ParticipantRequest;
import com.gayadi.server.travel.dto.request.TripStatusRequest;
import com.gayadi.server.travel.dto.request.UpdateTripRequest;
import com.gayadi.server.travel.dto.response.ParticipantResponse;
import com.gayadi.server.travel.dto.response.TripResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 여행과 참여자 관련 HTTP 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/v1/trips")
@Tag(name = "여행", description = "여행 생성·참여자·내 출발·귀가 장소 관리")
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
                    schema = @Schema(implementation = TripResponse.class))))
    public List<TripResponse> list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.listForUser(userId, status, limit, offset);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "여행 생성",
            description = "현재 사용자를 소유자로 여행을 만듭니다. "
                    + "소유자 출발·귀가 장소는 `departurePlaceId`/`returnPlaceId`로 넣을 수 있고, "
                    + "이후 `PATCH /api/v1/trips/{tripId}/participants/current`로 바꿀 수 있습니다.")
    @ApiResponse(responseCode = "201", description = "생성한 여행입니다.",
            content = @Content(schema = @Schema(implementation = TripResponse.class)))
    public TripResponse create(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateTripRequest request) {
        return service.createForUser(
                userId, request.getName(), request.parsedStartDate(), request.parsedEndDate(),
                request.getCities(), request.getDeparturePlaceId(), request.getReturnPlaceId());
    }

    @GetMapping("/{tripId}")
    @Operation(summary = "여행 상세 조회")
    @ApiResponse(responseCode = "200", description = "여행의 상세 정보입니다.",
            content = @Content(schema = @Schema(implementation = TripResponse.class)))
    public TripResponse get(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        service.requireMember(tripId, userId);
        return service.view(tripId);
    }

    @PatchMapping("/{tripId}")
    @Operation(summary = "여행 정보 수정")
    @ApiResponse(responseCode = "200", description = "수정한 여행입니다.",
            content = @Content(schema = @Schema(implementation = TripResponse.class)))
    public TripResponse update(
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
            content = @Content(schema = @Schema(implementation = TripResponse.class)))
    public TripResponse status(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody TripStatusRequest request) {
        return service.changeStatus(userId, tripId, request.getStatus());
    }

    @GetMapping("/{tripId}/participants")
    @Operation(summary = "여행 참여자 목록")
    @ApiResponse(responseCode = "200", description = "여행에 참여 중인 사용자 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ParticipantResponse.class))))
    public List<ParticipantResponse> participants(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId) {
        service.requireMember(tripId, userId);
        return service.members(tripId);
    }

    @PatchMapping("/{tripId}/participants/current")
    @Operation(summary = "내 출발·귀가 장소 수정",
            description = "현재 로그인한 참여자의 출발지와 귀가 장소를 바꿉니다. 기존 출발·귀가 경로 추천은 만료됩니다.")
    @ApiResponse(responseCode = "200", description = "수정한 참여자 정보입니다.",
            content = @Content(schema = @Schema(implementation = ParticipantResponse.class)))
    public ParticipantResponse updateCurrentPlaces(
            @AuthenticationPrincipal Long userId,
            @PathVariable long tripId,
            @Valid @RequestBody ParticipantRequest request) {
        return service.updateCurrentMemberPlaces(
                userId, tripId, request.getDeparturePlaceId(), request.getReturnPlaceId());
    }

    @PutMapping("/{tripId}/participants/{participantUserId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "여행 참여자 추가")
    @ApiResponse(responseCode = "201", description = "추가한 여행 참여자입니다.",
            content = @Content(schema = @Schema(implementation = ParticipantResponse.class)))
    public ParticipantResponse participant(
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

}
