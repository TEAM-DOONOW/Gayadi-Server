package com.gayadi.server.auth;

import com.gayadi.server.common.ApiException;
import com.gayadi.server.common.JsonSupport;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserService userService;
    private final JsonSupport json;

    public JwtAuthenticationFilter(JwtService jwtService, UserService userService, JsonSupport json) {
        this.jwtService = jwtService;
        this.userService = userService;
        this.json = json;
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
            unauthorized(response, request.getRequestURI(), "Bearer 방식의 로그인 토큰이 필요합니다.");
            return;
        }

        try {
            long userId = jwtService.parseAndGetUserId(authorization.substring(BEARER_PREFIX.length()).trim());
            if (!userService.isActive(userId)) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "사용할 수 없는 계정입니다.");
            }
            var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ApiException exception) {
            SecurityContextHolder.clearContext();
            unauthorized(response, request.getRequestURI(), exception.getMessage());
        }
    }

    private void unauthorized(HttpServletResponse response, String path, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", HttpStatus.UNAUTHORIZED.value());
        body.put("code", HttpStatus.UNAUTHORIZED.name());
        body.put("message", message);
        body.put("path", path);
        body.put("traceId", UUID.randomUUID().toString());
        body.put("details", Map.of());
        response.getWriter().write(json.write(body));
    }
}
