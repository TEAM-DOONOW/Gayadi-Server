package com.gayadi.server.auth;

import com.gayadi.server.common.dto.ApiResponses;
import com.gayadi.server.common.dto.ApiResponseMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "사용자", description = "현재 로그인한 사용자의 프로필과 계정을 관리합니다.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService service;
    private final ApiResponseMapper mapper;

    public UserController(UserService service, ApiResponseMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping("/current")
    @Operation(summary = "내 프로필 조회")
    @ApiResponse(responseCode = "200", description = "현재 사용자의 프로필입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.UserProfile.class)))
    public ApiResponses.UserProfile current(@AuthenticationPrincipal Long userId) {
        return mapper.toDto(service.profile(userId), ApiResponses.UserProfile.class);
    }

    @PatchMapping("/current")
    @Operation(summary = "내 프로필 수정")
    @ApiResponse(responseCode = "200", description = "수정한 프로필입니다.",
            content = @Content(schema = @Schema(implementation = ApiResponses.UserProfile.class)))
    public ApiResponses.UserProfile update(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return mapper.toDto(service.update(userId, request.getNickname(), request.getIntroduction()),
                ApiResponses.UserProfile.class);
    }

    @DeleteMapping("/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "계정 탈퇴")
    public void withdraw(@AuthenticationPrincipal Long userId) {
        service.withdraw(userId);
    }

    public static class UpdateProfileRequest {
        @NotBlank
        @Size(max = 10)
        private String nickname;
        @Size(max = 20)
        private String introduction;

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getIntroduction() {
            return introduction;
        }

        public void setIntroduction(String introduction) {
            this.introduction = introduction;
        }
    }
}
