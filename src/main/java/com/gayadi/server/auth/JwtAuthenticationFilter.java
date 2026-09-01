package com.gayadi.server.auth;

import com.gayadi.server.common.exception.BusinessException;
import com.gayadi.server.common.security.ApiAuthenticationEntryPoint;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** 인증과 사용자 계정 요청의 인증 정보를 검사하고 SecurityContext를 구성합니다. */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserService userService,
            ApiAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("Bearer 방식의 로그인 토큰이 필요합니다."));
            return;
        }

        try {
            long userId = jwtService.parseAndGetUserId(authorization.substring(BEARER_PREFIX.length()).trim());
            if (!userService.isActive(userId)) {
                throw new BusinessException(AuthErrorCode.AUTH_ACCOUNT_UNAVAILABLE);
            }
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("유효하지 않은 로그인 토큰입니다.", exception));
        }
    }
}
