package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.config.ApiSuccessSchemas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "계정 등록과 로그인 토큰 발급을 처리합니다.")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "계정 등록")
    @ApiResponse(responseCode = "201", description = "계정과 로그인 토큰을 발급했습니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.AuthToken.class)))
    public Map<String, Object> signup(@Valid @RequestBody SignupRequest request) {
        return service.signup(request.getEmail(), request.getPassword(), request.getNickname());
    }

    @PostMapping("/tokens")
    @Operation(summary = "로그인 토큰 발급")
    @ApiResponse(responseCode = "200", description = "로그인 토큰을 발급했습니다.",
            content = @Content(schema = @Schema(implementation = ApiSuccessSchemas.AuthToken.class)))
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.getEmail(), request.getPassword());
    }

    public static class SignupRequest {
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;
        @NotBlank
        @Size(min = 6, max = 72)
        private String password;
        @NotBlank
        @Size(max = 10)
        @Pattern(regexp = "^[\\p{L}\\p{N} _-]+$", message = "닉네임은 문자, 숫자, 공백, 밑줄, 하이픈만 사용할 수 있습니다.")
        private String nickname;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }

    public static class LoginRequest {
        @NotBlank
        @Email
        @Size(max = 255)
        private String email;
        @NotBlank
        @Size(max = 72)
        private String password;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
