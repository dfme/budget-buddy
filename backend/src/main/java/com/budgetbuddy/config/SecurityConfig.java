package com.budgetbuddy.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Stateless-Security-Konfiguration (BE-AUTH-01, ADR-7).
 *
 * <p>Kein Session-State, kein httpBasic/formLogin: Authentifizierung erfolgt ausschliesslich
 * über das JWT im httpOnly-Cookie (Filter folgt in Schritt 4). Nicht authentifizierte Zugriffe
 * auf geschützte Pfade beantwortet der {@link HttpStatusEntryPoint} mit 401 statt eines
 * Browser-Login-Prompts. CSRF ist deaktiviert, da das JWT-Cookie {@code SameSite=Strict} nutzt.
 *
 * <p>Frei zugänglich bleiben Swagger UI, OpenAPI-Docs und der Actuator-Health-Endpoint.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex ->
                ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
        return http.build();
    }
}
