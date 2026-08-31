package com.gayadi.server.auth;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.auth.dto.response.AccountResponse;
import com.gayadi.server.auth.dto.response.AuthTokenResponse;
import com.gayadi.server.auth.query.LoginAccountQueryResult;
import com.gayadi.server.auth.query.UserQueryResult;
import com.gayadi.server.auth.model.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Locale;

/** 인증과 사용자 계정 유스케이스와 업무 규칙을 처리합니다. */
@Service
public class AuthService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserService userService;
    private final JwtService jwtService;
    private final AuthRepository repository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserService userService, JwtService jwtService, AuthRepository repository) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.repository = repository;
    }

    /** 이메일 계정을 생성하고 로그인 토큰을 발급합니다. */
    @Transactional
    public AuthTokenResponse signup(String email, String password, String nickname) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            long id = repository.createAccount(
                    nickname.trim(), normalizedEmail, passwordEncoder.encode(password));
            return loginResult(id);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new BusinessException(AuthErrorCode.AUTH_EMAIL_ALREADY_REGISTERED);
            }
            throw exception;
        }
    }

    private boolean isDuplicateEmail(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null || cause.getMessage() == null
                ? "" : cause.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("uk_users_email");
    }

    /** 계정 상태와 비밀번호를 검증해 로그인 토큰을 발급합니다. */
    @Transactional(noRollbackFor = BusinessException.class)
    public AuthTokenResponse login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        LoginAccountQueryResult user = repository.findByEmailForUpdate(normalizedEmail)
                .orElseThrow(() -> unauthorized());
        long userId = user.userId();
        if (user.status() != UserStatus.ACTIVE) {
            throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE);
        }

        String hash = user.passwordHash();
        LocalDateTime lockedUntil = user.loginLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            if (hash != null && passwordEncoder.matches(password, hash)) {
                repository.clearFailedLogins(userId);
                return loginResult(userId);
            }
            throw new BusinessException(AuthErrorCode.AUTH_LOGIN_RATE_LIMITED);
        }

        if (hash == null || !passwordEncoder.matches(password, hash)) {
            repository.recordFailedLogin(
                    userId, MAX_FAILED_ATTEMPTS, LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            throw unauthorized();
        }

        repository.clearFailedLogins(userId);
        return loginResult(userId);
    }

    private BusinessException unauthorized() {
        return new BusinessException(AuthErrorCode.AUTH_INVALID_CREDENTIALS);
    }

    private AuthTokenResponse loginResult(long userId) {
        UserQueryResult user = userService.get(userId);
        String email = user.email() == null ? "" : user.email();
        return new AuthTokenResponse(
                jwtService.issue(userId, email),
                "Bearer",
                jwtService.getExpiresIn().toSeconds(),
                new AccountResponse(
                        user.id(),
                        user.nickname(),
                        user.email(),
                        user.introduction(),
                        user.profileImageUrl(),
                        user.status(),
                        user.lastLoginAt(),
                        user.createdAt(),
                        user.updatedAt()));
    }
}
