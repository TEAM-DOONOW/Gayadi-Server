package com.gayadi.server.friendship;

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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/friendships")
@Tag(name = "친구", description = "친구 요청, 수락, 거절과 차단을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class FriendshipController {

    private final FriendshipService service;
    private final ApiResponseMapper mapper;

    public FriendshipController(FriendshipService service, ApiResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "친구 관계 목록")
    @ApiResponse(responseCode = "200", description = "현재 사용자의 친구 관계 목록입니다.",
            content = @Content(array = @ArraySchema(
                    schema = @Schema(implementation = ApiResponses.Friendship.class))))
    public List<ApiResponses.Friendship> friendships(
            @AuthenticationPrincipal Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "50")
            @Min(value = 1, message = "조회 개수는 1개 이상이어야 합니다.")
            @Max(value = 100, message = "한 번에 100개까지 조회할 수 있습니다.") int limit,
            @RequestParam(defaultValue = "0")
            @PositiveOrZero(message = "조회 시작 위치는 0 이상이어야 합니다.") int offset) {
        return mapper.toDtoList(service.list(userId, status, limit, offset), ApiResponses.Friendship.class);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "친구 요청 등록")
    @ApiResponse(responseCode = "201", description = "등록한 친구 요청입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Friendship.class)))
    public ApiResponses.Friendship friendship(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody FriendshipRequest request) {
        return mapper.toDto(service.create(userId, request.getTargetUserId()), ApiResponses.Friendship.class);
    }

    @PatchMapping("/{friendshipId}")
    @Operation(summary = "친구 관계 상태 변경")
    @ApiResponse(responseCode = "200", description = "상태를 바꾼 친구 관계입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.Friendship.class)))
    public ApiResponses.Friendship friendshipStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive(message = "친구 관계 번호는 1 이상이어야 합니다.") long friendshipId,
            @Valid @RequestBody FriendshipStatusRequest request) {
        return mapper.toDto(
                service.update(userId, friendshipId, request.getStatus(), request.getVersion()),
                ApiResponses.Friendship.class);
    }

    @DeleteMapping("/{friendshipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "친구 관계 삭제 또는 요청 취소")
    public void friendshipDeletion(
            @AuthenticationPrincipal Long userId,
            @PathVariable @Positive(message = "친구 관계 번호는 1 이상이어야 합니다.") long friendshipId) {
        service.delete(userId, friendshipId);
    }

    public static class FriendshipRequest {
        @NotNull(message = "친구로 요청할 사용자 번호가 필요합니다.")
        @Positive(message = "사용자 번호는 1 이상이어야 합니다.")
        private Long targetUserId;

        public Long getTargetUserId() {
            return targetUserId;
        }

        public void setTargetUserId(Long targetUserId) {
            this.targetUserId = targetUserId;
        }
    }

    public static class FriendshipStatusRequest {
        @NotNull(message = "바꿀 친구 관계 상태가 필요합니다.")
        private FriendshipStatus status;
        @PositiveOrZero(message = "친구 관계 버전은 0 이상이어야 합니다.")
        private Integer version;

        public FriendshipStatus getStatus() {
            return status;
        }

        public void setStatus(FriendshipStatus status) {
            this.status = status;
        }

        public Integer getVersion() {
            return version;
        }

        public void setVersion(Integer version) {
            this.version = version;
        }
    }
}
