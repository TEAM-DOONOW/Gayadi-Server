package com.gayadi.server.invitation;

import com.gayadi.server.common.dto.ApiResponseMapper;
import com.gayadi.server.common.dto.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "초대", description = "여행 초대 코드와 초대를 통한 참여를 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class InvitationController {

    private final InvitationService service;
    private final ApiResponseMapper mapper;

    public InvitationController(InvitationService service, ApiResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/trips/{tripId}/invitations")
    @Operation(summary = "여행 초대 목록")
    @ApiResponse(responseCode = "200", description = "여행에서 발급한 초대 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiResponses.Invitation.class))))
    public List<ApiResponses.Invitation> invitations(
            @PathVariable long tripId,
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "30") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return mapper.toDtoList(service.list(tripId, userId, limit, offset), ApiResponses.Invitation.class);
    }

    @PostMapping("/trips/{tripId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "여행 초대 발급")
    @ApiResponse(responseCode = "201", description = "발급한 초대입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Invitation.class)))
    public ApiResponses.Invitation invitation(
            @PathVariable long tripId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateInvitationRequest request) {
        return mapper.toDto(
                service.create(tripId, userId, request.getInviteeUserId(), request.getExpiresAt()),
                ApiResponses.Invitation.class);
    }

    @PatchMapping("/trips/{tripId}/invitations/{invitationId}")
    @Operation(summary = "여행 초대 상태 변경")
    @ApiResponse(responseCode = "200", description = "상태를 바꾼 초대입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Invitation.class)))
    public ApiResponses.Invitation invitationStatus(
            @PathVariable long tripId,
            @PathVariable long invitationId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateInvitationRequest request) {
        return mapper.toDto(
                service.updateStatus(tripId, invitationId, userId, request.getStatus()),
                ApiResponses.Invitation.class);
    }

    @PostMapping("/trip-memberships")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "초대 코드로 여행 참여")
    @ApiResponse(responseCode = "201", description = "여행 참여 결과입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Membership.class)))
    public ApiResponses.Membership membership(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody JoinTripRequest request) {
        return mapper.toDto(service.join(
                        userId,
                        request.getInviteCode(),
                        request.getDeparturePlaceId(),
                        request.getReturnPlaceId()),
                ApiResponses.Membership.class);
    }

    public static class CreateInvitationRequest {
        @NotNull(message = "초대할 사용자 번호가 필요합니다.")
        private Long inviteeUserId;
        @Future(message = "초대 만료 시각은 현재보다 뒤여야 합니다.")
        private LocalDateTime expiresAt;

        public Long getInviteeUserId() { return inviteeUserId; }
        public void setInviteeUserId(Long inviteeUserId) { this.inviteeUserId = inviteeUserId; }
        public LocalDateTime getExpiresAt() { return expiresAt; }
        public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    }

    public static class UpdateInvitationRequest {
        @NotNull(message = "바꿀 초대 상태가 필요합니다.")
        private InvitationService.InvitationDecision status;

        public InvitationService.InvitationDecision getStatus() { return status; }
        public void setStatus(InvitationService.InvitationDecision status) { this.status = status; }
    }

    public static class JoinTripRequest {
        @NotBlank(message = "초대 코드가 필요합니다.")
        @Pattern(regexp = "(?i)^(?:[A-Z0-9]{6}|[A-Z0-9]{8})$",
                message = "여행 공유 코드는 6자리, 특정 사용자 초대 코드는 8자리여야 합니다.")
        private String inviteCode;
        private Long departurePlaceId;
        private Long returnPlaceId;

        public String getInviteCode() { return inviteCode; }
        public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }
        public Long getDeparturePlaceId() { return departurePlaceId; }
        public void setDeparturePlaceId(Long departurePlaceId) { this.departurePlaceId = departurePlaceId; }
        public Long getReturnPlaceId() { return returnPlaceId; }
        public void setReturnPlaceId(Long returnPlaceId) { this.returnPlaceId = returnPlaceId; }
    }
}
