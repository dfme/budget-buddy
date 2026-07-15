package com.budgetbuddy.config;

import com.budgetbuddy.auth.JwtCookieAuthenticationFilter;
import com.budgetbuddy.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless-Security-Konfiguration (BE-AUTH-01, ADR-7).
 *
 * <p>Kein Session-State, kein httpBasic/formLogin: Authentifizierung erfolgt ausschliesslich
 * über das JWT im httpOnly-Cookie via {@link JwtCookieAuthenticationFilter}. Nicht
 * authentifizierte Zugriffe auf geschützte Pfade beantwortet der {@link HttpStatusEntryPoint}
 * mit 401 statt eines Browser-Login-Prompts. CSRF ist deaktiviert, da das JWT-Cookie
 * {@code SameSite=Strict} nutzt.
 *
 * <p>Frei zugänglich bleiben Swagger UI, OpenAPI-Docs sowie die Actuator-Endpoints Health und
 * Info. Info meldet nur den deployten Commit-SHA (INFRA-08) und muss ohne Auth erreichbar sein,
 * weil der CD-Smoke-Test die Version vor dem Login prüft.
 *
 * <p>Zusätzlich werden die im JAR gebündelte Angular-SPA und ihre statischen Assets
 * öffentlich per GET ausgeliefert (INFRA-05, ADR-10 Single-Artifact). Da die API keinen
 * gemeinsamen Prefix hat (z.B. {@code /users/me}), bleiben die freigegebenen Pfade bewusst
 * eng gefasst (Default-Deny), statt GET pauschal zu öffnen — versehentliche Exposition wäre
 * bei Transaktionsdaten Risiko #2 (Datenleck). SPA-Routen werden in {@code SPA_GET_PATHS}
 * gepflegt; neue Frontend-Routen müssen hier UND in {@code SpaForwardController} ergänzt werden.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/auth/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/actuator/info"
    };

    /**
     * Öffentlich per GET erreichbare Pfade für die Auslieferung der SPA: die statischen
     * Build-Artefakte (flach unter {@code static/}: {@code main-*.js}, {@code styles-*.css},
     * {@code favicon.ico}, {@code 3rdpartylicenses.txt}, optional {@code assets/**}), die
     * Einstiegsseite {@code /} bzw. {@code /index.html} sowie die client-seitigen
     * Angular-Routen (Deep-Link/Hard-Reload → {@code SpaForwardController}).
     *
     * <p>Bei neuen Frontend-Routen: hier und in {@code SpaForwardController} ergänzen.
     */
    private static final String[] SPA_GET_PATHS = {
        "/",
        "/index.html",
        "/favicon.ico",
        "/*.js",
        "/*.css",
        "/*.txt",
        "/assets/**",
        "/dashboard/**",
        "/login/**"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers(HttpMethod.GET, SPA_GET_PATHS).permitAll()
                .anyRequest().authenticated())
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex ->
                ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
            .addFilterBefore(new JwtCookieAuthenticationFilter(jwtService),
                UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** bcrypt-Hashing für Passwörter (BE-AUTH-03, ADR-7). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
