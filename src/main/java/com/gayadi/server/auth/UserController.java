package com.gayadi.server.auth;

import com.gayadi.server.auth.dto.request.UpdateProfileRequest;
import com.gayadi.server.auth.dto.response.UserProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 인증과 사용자 계정 관련 HTTP 요청과 응답을 처리합니다. */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "사용자", description = "현재 로그인한 사용자의 프로필과 계정을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/current")
    @Operation(summary = "내 프로필 조회")
    @ApiResponse(responseCode = "200", description = "현재 사용자의 프로필입니다.",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class)))
    public UserProfileResponse current(@AuthenticationPrincipal Long userId) {
        return service.profile(userId);
    }

    @PatchMapping("/current")
    @Operation(summary = "내 프로필 수정")
    @ApiResponse(responseCode = "200", description = "수정한 프로필입니다.",
            content = @Content(schema = @Schema(implementation = UserProfileResponse.class)))
    public UserProfileResponse update(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(userId, request.nickname(), request.introduction());
    }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "계정 탈퇴")
    public void withdraw(@AuthenticationPrincipal Long userId) {
        service.withdraw(userId);
    }
}
