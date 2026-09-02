package com.gayadi.server.config;

import com.gayadi.server.auth.JwtAuthenticationFilter;
import com.gayadi.server.common.security.ApiAccessDeniedHandler;
import com.gayadi.server.common.security.ApiAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/registrations",
                                "/api/v1/auth/tokens",
                                "/api/v1/auth/google-tokens",
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
                                "/api/v1/tour/discover",
                                "/api/v1/tour/areas",
                                "/api/v1/legal-documents/**",
                                "/api/v1/notices/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
