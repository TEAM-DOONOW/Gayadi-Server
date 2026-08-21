package com.gayadi.server.config;

import com.gayadi.server.auth.JwtAuthenticationFilter;
import com.gayadi.server.common.JsonSupport;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            JsonSupport json) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/registrations",
                                "/api/v1/auth/tokens",
                                "/api/openapi/**",
                                "/api/docs/**",
                                "/api/docs",
                                "/api/swagger-ui/**",
                                "/swagger-ui/**",
                                "/actuator/health",
                                "/actuator/info",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/surveys/**",
                                "/api/v1/places/**",
                                "/api/v1/tour/**",
                                "/api/v1/weather/**",
                                "/api/v1/legal-documents/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) ->
                                error(response, json, HttpStatus.UNAUTHORIZED,
                                        "로그인이 필요합니다.", request.getRequestURI()))
                        .accessDeniedHandler((request, response, exception) ->
                                error(response, json, HttpStatus.FORBIDDEN,
                                        "이 요청을 처리할 권한이 없습니다.", request.getRequestURI())))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private static void error(
            HttpServletResponse response,
            JsonSupport json,
            HttpStatus status,
            String message,
            String path) throws java.io.IOException {
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("code", status.name());
        body.put("message", message);
        body.put("path", path);
        body.put("traceId", UUID.randomUUID().toString());
        body.put("details", Map.of());
        response.getWriter().write(json.write(body));
    }
}
