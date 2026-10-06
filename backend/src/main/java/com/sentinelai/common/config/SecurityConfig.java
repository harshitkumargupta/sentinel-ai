package com.sentinelai.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelai.auth.security.JwtAuthenticationFilter;
import com.sentinelai.site.security.ApiKeyAuthenticationFilter;
import com.sentinelai.common.web.ApiResponse;
import com.sentinelai.common.web.ApiResponse.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final WebSecurityProperties webSecurity;

    /** Endpoints reachable without authentication. Actuator is limited to health/info/prometheus. */
    private static final String[] PUBLIC = {
            "/api/health",
            "/api/auth/login",
            "/api/auth/refresh",
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            "/actuator/prometheus",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // Stateless bearer-token API: no cookies/sessions, so CSRF tokens are not applicable.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(this::securityHeaders)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        // Any other actuator endpoint (metrics, env, …) is ADMIN-only.
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(apiKeyAuthenticationFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    private void securityHeaders(HeadersConfigurer<HttpSecurity> headers) {
        WebSecurityProperties.Headers h = webSecurity.getHeaders();
        headers
                .contentTypeOptions(c -> {}) // X-Content-Type-Options: nosniff
                .frameOptions(f -> f.disable()) // replaced by the explicit value + CSP frame-ancestors below
                .addHeaderWriter(new StaticHeadersWriter("Content-Security-Policy", h.getContentSecurityPolicy()))
                .addHeaderWriter(new StaticHeadersWriter("X-Frame-Options", h.getFrameOptions()))
                .addHeaderWriter(new StaticHeadersWriter("Referrer-Policy", h.getReferrerPolicy()))
                .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy", h.getPermissionsPolicy()));
        if (webSecurity.isHstsEnabled()) {
            headers.httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(webSecurity.getHstsMaxAgeSeconds()));
        } else {
            headers.httpStrictTransportSecurity(hsts -> hsts.disable());
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        WebSecurityProperties.Cors c = webSecurity.getCors();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(c.getAllowedOrigins());
        config.setAllowedMethods(c.getAllowedMethods());
        config.setAllowedHeaders(c.getAllowedHeaders());
        config.setExposedHeaders(c.getExposedHeaders());
        config.setAllowCredentials(c.isAllowCredentials());
        config.setMaxAge(c.getMaxAgeSeconds());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        ApiError.of("UNAUTHORIZED", "Authentication is required to access this resource"));
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) ->
                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                        ApiError.of("FORBIDDEN", "You do not have permission to perform this action"));
    }

    private void writeError(HttpServletResponse response, int status, ApiError error) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(error));
    }
}
