package com.gayadi.server.auth;

import com.gayadi.server.auth.dto.request.GoogleLoginRequest;
import com.gayadi.server.auth.dto.request.LoginRequest;
import com.gayadi.server.auth.dto.request.RefreshTokenRequest;
import com.gayadi.server.auth.dto.request.SignupRequest;
import com.gayadi.server.auth.dto.response.AuthTokenResponse;
import com.gayadi.server.common.response.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** 인증과 로그인 토큰 발급 HTTP 요청을 처리합니다. */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "인증", description = "Google 로그인과 개발용 이메일 계정 토큰 발급")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    /** 이메일 계정을 만들고 로그인 JWT를 발급합니다. */
    @PostMapping("/registrations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "이메일 계정 등록",
            description = "개발용 이메일·비밀번호 계정입니다. 앱 로그인은 `POST /api/v1/auth/google-tokens`를 사용합니다. "
                    + "성공 시 Bearer JWT와 계정 정보를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "계정과 로그인 토큰을 발급했습니다.",
                    content = @Content(schema = @Schema(implementation = AuthTokenResponse.class))),
            @ApiResponse(responseCode = "409", description = "이미 가입된 이메일입니다. AUTH_EMAIL_ALREADY_REGISTERED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthTokenResponse signup(@Valid @RequestBody SignupRequest request) {
        return service.signup(request.email(), request.password(), request.nickname());
    }

    /** 이메일 계정으로 로그인 JWT를 발급합니다. */
    @PostMapping("/tokens")
    @Operation(
            summary = "이메일 로그인 토큰 발급",
            description = "개발용 이메일 로그인입니다. 앱 로그인은 `POST /api/v1/auth/google-tokens`를 사용합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 토큰을 발급했습니다.",
                    content = @Content(schema = @Schema(implementation = AuthTokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "이메일 또는 비밀번호가 올바르지 않습니다. AUTH_INVALID_CREDENTIALS",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "탈퇴했거나 사용할 수 없는 계정입니다. AUTH_ACCOUNT_UNAVAILABLE",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "로그인 시도가 많아 잠시 잠겼습니다. AUTH_LOGIN_RATE_LIMITED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return service.login(request.email(), request.password());
    }

    /** Google ID 토큰을 검증하고 서버 JWT를 발급합니다. */
    @PostMapping("/google-tokens")
    @Operation(
            summary = "Google 로그인 토큰 발급",
            description = "Android Google 로그인 ID 토큰을 검증하고 서버 JWT를 발급합니다. "
                    + "같은 Google 계정이면 기존 사용자를 재사용하고, 없으면 계정을 만듭니다. "
                    + "GOOGLE_CLIENT_ID가 없으면 503 AUTH_GOOGLE_NOT_CONFIGURED를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Google 계정으로 로그인 토큰을 발급했습니다.",
                    content = @Content(schema = @Schema(implementation = AuthTokenResponse.class))),
            @ApiResponse(responseCode = "401", description = "Google ID 토큰이 올바르지 않거나 만료되었습니다.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "이 Google 계정은 다른 사용자에게 연결되어 있습니다. AUTH_GOOGLE_ACCOUNT_CONFLICT",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Google 로그인이 설정되지 않았습니다. AUTH_GOOGLE_NOT_CONFIGURED",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
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
