package com.gayadi.server.auth;

import com.gayadi.server.auth.dto.request.GoogleLoginRequest;
import com.gayadi.server.auth.dto.request.LoginRequest;
import com.gayadi.server.auth.dto.request.RefreshTokenRequest;
import com.gayadi.server.auth.dto.request.SignupRequest;
import com.gayadi.server.auth.dto.response.AuthTokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 인증과 사용자 계정 관련 HTTP 요청과 응답을 처리합니다. */
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
            content = @Content(schema = @Schema(implementation = AuthTokenResponse.class)))
    public AuthTokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return service.signup(request.email(), request.password(), request.nickname());
    }

    @PostMapping("/tokens")
    @Operation(summary = "로그인 토큰 발급")
    @ApiResponse(responseCode = "200", description = "로그인 토큰을 발급했습니다.",
            content = @Content(schema = @Schema(implementation = AuthTokenResponse.class)))
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.email(), request.password());
    }

    @PostMapping("/google-tokens")
    @Operation(
            summary = "Google 로그인 토큰 발급",
            description = "Android Google 로그인 ID 토큰을 검증하고 서버 JWT를 발급합니다. "
                    + "GOOGLE_CLIENT_ID가 없으면 503을 반환합니다.")
    @ApiResponse(responseCode = "200", description = "Google 계정으로 로그인 토큰을 발급했습니다.",
            content = @Content(schema = @Schema(implementation = AuthTokenResponse.class)))
    public AuthTokenResponse googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        return service.loginWithGoogle(request.idToken());
    }

    @PostMapping("/token-refreshes")
    @Operation(summary = "로그인 토큰 갱신")
    @ApiResponse(responseCode = "200", description = "기존 Refresh Token을 회전하고 새 토큰 쌍을 발급했습니다.",
            content = @Content(schema = @Schema(implementation = AuthTokenResponse.class)))
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return service.refresh(request.refreshToken());
    }

    @DeleteMapping("/sessions/current")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "현재 로그인 세션 종료")
    @ApiResponse(responseCode = "204", description = "현재 Refresh 세션을 종료했습니다.")
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        service.logout(request.refreshToken());
    }
}
