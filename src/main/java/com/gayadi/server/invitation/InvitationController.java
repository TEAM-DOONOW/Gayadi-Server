package com.gayadi.server.invitation;

import com.gayadi.server.invitation.dto.request.InvitationCreateRequest;
import com.gayadi.server.invitation.dto.request.InvitationStatusUpdateRequest;
import com.gayadi.server.invitation.dto.request.TripMembershipCreateRequest;
import com.gayadi.server.invitation.dto.response.InvitationResponse;
import com.gayadi.server.travel.dto.response.MembershipResponse;
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

/** 여행 초대 관련 HTTP 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "초대", description = "여행 초대 코드와 초대를 통한 참여를 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class InvitationController {

    private final InvitationService service;

    public InvitationController(InvitationService service) {
        this.service = service;
    }

    @GetMapping("/trips/{tripId}/invitations")
    @Operation(summary = "여행 초대 목록")
    @ApiResponse(responseCode = "200", description = "여행에서 발급한 초대 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = InvitationResponse.class))))
    public List<InvitationResponse> invitations(
            @PathVariable long tripId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return service.list(tripId, userId, limit, offset);
    }

    @PostMapping("/trips/{tripId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "여행 초대 발급")
    @ApiResponse(responseCode = "201", description = "발급한 초대입니다.",
            content = @Content(schema = @Schema(implementation = InvitationResponse.class)))
    public InvitationResponse invitation(
            @PathVariable long tripId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InvitationCreateRequest request) {
        return service.create(tripId, userId, request.inviteeUserId(), request.expiresAt());
    }

    @PatchMapping("/trips/{tripId}/invitations/{invitationId}")
    @Operation(summary = "여행 초대 상태 변경")
    @ApiResponse(responseCode = "200", description = "상태를 바꾼 초대입니다.",
            content = @Content(schema = @Schema(implementation = InvitationResponse.class)))
    public InvitationResponse invitationStatus(
            @PathVariable long tripId,
            @PathVariable long invitationId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody InvitationStatusUpdateRequest request) {
        return service.updateStatus(tripId, invitationId, userId, request.status());
    }

    @PostMapping("/trip-memberships")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "초대 코드로 여행 참여")
    @ApiResponse(responseCode = "201", description = "여행 참여 결과입니다.",
            content = @Content(schema = @Schema(implementation = MembershipResponse.class)))
    public MembershipResponse membership(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody TripMembershipCreateRequest request) {
        return service.join(
                userId,
                request.inviteCode(),
                request.departurePlaceId(),
                request.returnPlaceId());
    }
}
