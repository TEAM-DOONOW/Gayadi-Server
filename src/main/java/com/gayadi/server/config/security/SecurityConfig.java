package com.gayadi.server.config.security;

import com.gayadi.server.auth.JwtAuthenticationFilter;
import com.gayadi.server.auth.AuthRateLimitFilter;
import com.gayadi.server.common.security.ApiAccessDeniedHandler;
import com.gayadi.server.common.security.ApiAuthenticationEntryPoint;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties({CorsProperties.class, AuthRateLimitProperties.class})
public class SecurityConfig {

    private static final List<String> CORS_ALLOWED_METHODS = List.of(
            HttpMethod.GET.name(),
            HttpMethod.POST.name(),
            HttpMethod.PUT.name(),
            HttpMethod.PATCH.name(),
            HttpMethod.DELETE.name(),
            HttpMethod.OPTIONS.name());

    private static final List<String> CORS_ALLOWED_HEADERS = List.of(
            "Authorization",
            "Content-Type",
            "Accept",
            "Accept-Language");

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectProvider<AuthRateLimitFilter> authRateLimitFilter,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler) throws Exception {
        authRateLimitFilter.ifAvailable(filter ->
                http.addFilterBefore(filter, JwtAuthenticationFilter.class));

        return http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .permissionsPolicyHeader(permissions -> permissions
                                .policy("camera=(), microphone=(), geolocation=(), payment=()"))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; object-src 'none'; "
                                        + "frame-ancestors 'none'; base-uri 'self'")
                                .reportOnly())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(Duration.ofDays(365).toSeconds())))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/v1/auth/registrations",
                                "/api/v1/auth/tokens",
                                "/api/v1/auth/google-tokens",
                                "/api/v1/auth/token-refreshes",
                                "/api/v1/auth/sessions/current",
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

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(CORS_ALLOWED_METHODS);
        configuration.setAllowedHeaders(CORS_ALLOWED_HEADERS);
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
