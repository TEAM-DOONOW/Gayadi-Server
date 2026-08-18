package com.gayadi.server.auth;

import com.gayadi.server.config.ApiSuccessSchemas;
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

import java.util.Map;

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
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.UserProfile.class)))
    public Map<String, Object> current(@AuthenticationPrincipal Long userId) {
        return service.profile(userId);
    }

    @PatchMapping("/current")
    @Operation(summary = "내 프로필 수정")
    @ApiResponse(responseCode = "200", description = "수정한 프로필입니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.UserProfile.class)))
    public Map<String, Object> update(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UpdateProfileRequest request) {
        return service.update(userId, request.getNickname(), request.getIntroduction());
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
