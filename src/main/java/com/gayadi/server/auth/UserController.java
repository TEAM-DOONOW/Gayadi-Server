package com.gayadi.server.auth;

import com.gayadi.server.auth.dto.request.UpdateProfileRequest;
import com.gayadi.server.auth.dto.response.UserProfileResponse;
import com.gayadi.server.common.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 현재 로그인한 사용자의 프로필과 탈퇴 HTTP 요청을 처리합니다. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "사용자", description = "현재 로그인한 사용자의 프로필 조회·수정·탈퇴")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    /** 로그인 JWT의 사용자 프로필과 최신 여행 성향을 조회합니다. */
    @GetMapping("/current")
    @Operation(
            summary = "내 프로필 조회",
            description = "Authorization Bearer JWT의 사용자 프로필과 최근 여행 성향을 반환합니다. "
                    + "사용자 식별자는 토큰에서만 읽고 요청 본문·질의로는 받지 않습니다.")
    @ApiResponse(responseCode = "200", description = "현재 사용자의 프로필입니다.",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class)))
    public UserProfileResponse current(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return service.profile(userId);
    }

    /** 로그인 사용자의 닉네임과 한 줄 소개를 수정합니다. */
    @PatchMapping("/current")
    @Operation(
            summary = "내 프로필 수정",
            description = "닉네임과 한 줄 소개를 수정합니다. Google 프로필 사진은 최초 가입 시에만 저장됩니다.")
    @ApiResponse(responseCode = "200", description = "수정한 프로필입니다.",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class)))
    public UserProfileResponse update(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(userId, request.nickname(), request.introduction());
    }

    /** 계정을 탈퇴하고 개인정보를 비식별화합니다. */
    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "계정 탈퇴",
            description = "소셜 로그인·기기·찜·친구·설문 등 개인 데이터를 제거하고 계정을 WITHDRAW로 익명화합니다. "
                    + "준비 중이거나 진행 중인 소유 여행이 있으면 409 USER_ACTIVE_OWNED_TRIP_EXISTS입니다. "
                    + "탈퇴 후 같은 JWT는 403 AUTH_ACCOUNT_UNAVAILABLE입니다. 같은 Google 계정으로 다시 로그인하면 새 사용자가 됩니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "계정을 탈퇴했습니다."),
            @ApiResponse(responseCode = "409", description = "진행 중이거나 준비 중인 소유 여행을 먼저 취소해야 합니다. USER_ACTIVE_OWNED_TRIP_EXISTS",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void withdraw(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        service.withdraw(userId);
    }
}
