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
 * <p>Alle REST-Endpoints liegen unter dem gemeinsamen Präfix {@code /api/**} (INFRA-17); das
 * macht die Freigabe der im JAR gebündelten Angular-SPA (INFRA-05, ADR-10 Single-Artifact)
 * strukturell statt enumerativ: jedes GET ausserhalb von {@code /api/**} ist die SPA-Shell oder
 * ein statisches Asset und damit öffentlich, jedes GET/POST/... unter {@code /api/**} (ausser
 * {@code /api/auth/**}) verlangt Auth. Vorher mussten {@code SecurityConfig} und
 * {@link SpaForwardController} jede neue Angular-Route einzeln kennen — das lief zweimal
 * auseinander (INFRA-14, #126). Die einzige verbleibende Enumeration sind die Infrastruktur-Pfade
 * unterhalb von {@link #PUBLIC_PATHS}, die weder API noch SPA-Route sind.
 */
@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/api/auth/**",
        "/v3/api-docs/**",
        "/swagger-ui/**",
        "/swagger-ui.html",
        "/actuator/health",
        "/actuator/info",
        // Springs ERROR-Dispatch: @ResponseStatus auf einer Exception (z.B. 408 Timeout,
        // 409 Duplikat beim PDF-Import) läuft über response.sendError() und wird intern auf
        // /error weitergeleitet. Der JwtCookieAuthenticationFilter überspringt den
        // ERROR-Dispatch (OncePerRequestFilter-Default) — ohne diese Freigabe antwortet
        // anyRequest().authenticated() dort mit 401 und überschreibt den echten Status. /error
        // liefert nur Status und Standard-Fehlerattribute, nie Nutzdaten (Risiko #2 unberührt).
        "/error"
    };

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwtService) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/**").permitAll()
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
