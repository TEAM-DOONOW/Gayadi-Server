package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.auth.persistence.UserAccount;
import com.gayadi.server.auth.persistence.UserAccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Locale;

@Service
public class AuthService {

    private final UserService userService;
    private final JwtService jwtService;
    private final UserAccountRepository accounts;
    private final AuthProperties properties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            UserService userService,
            JwtService jwtService,
            UserAccountRepository accounts,
            AuthProperties properties) {
        this.userService = userService;
        this.jwtService = jwtService;
        this.accounts = accounts;
        this.properties = properties;
    }

    @Transactional
    public Map<String, Object> signup(String email, String password, String nickname) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        try {
            UserAccount account = accounts.saveAndFlush(new UserAccount(
                    nickname.trim(), normalizedEmail, passwordEncoder.encode(password)));
            return loginResult(account.getId());
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateEmail(exception)) {
                throw new ApiException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
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

    @Transactional(noRollbackFor = ApiException.class)
    public Map<String, Object> login(String email, String password) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        UserAccount user = accounts.findForLogin(normalizedEmail)
                .orElseThrow(() -> unauthorized());
        long userId = user.getId();
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "사용할 수 없는 계정입니다.");
        }

        String hash = user.getPasswordHash();
        LocalDateTime lockedUntil = user.getLoginLockedUntil();
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            if (hash != null && passwordEncoder.matches(password, hash)) {
                user.recordSuccessfulLogin();
                return loginResult(userId);
            }
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS,
                    "로그인 시도가 많아 잠시 잠겼습니다. 잠시 후 다시 시도해 주세요.");
        }

        if (hash == null || !passwordEncoder.matches(password, hash)) {
            user.recordFailedLogin(properties.maximumFailedAttempts(), properties.lockMinutes());
            throw unauthorized();
        }

        user.recordSuccessfulLogin();
        return loginResult(userId);
    }

    private ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
    }

    private Map<String, Object> loginResult(long userId) {
        Map<String, Object> user = userService.get(userId);
        String email = user.get("email") == null ? "" : user.get("email").toString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accessToken", jwtService.issue(userId, email));
        result.put("tokenType", "Bearer");
        result.put("expiresIn", jwtService.getExpiresIn().toSeconds());
        result.put("user", user);
        return result;
    }
}
