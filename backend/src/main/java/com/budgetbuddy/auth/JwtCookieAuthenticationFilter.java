package com.budgetbuddy.auth;

import com.budgetbuddy.config.LogContext;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Liest das JWT aus dem httpOnly-Cookie, validiert es (HS256) und befüllt bei Erfolg den
 * {@link SecurityContextHolder} mit der User-ID als Principal (BE-AUTH-01, ADR-7).
 *
 * <p>Bei fehlendem, ungültigem, abgelaufenem oder mit veralteter {@code tokenVersion} versehenem
 * Token bleibt der SecurityContext leer; die Autorisierung in {@code SecurityConfig} antwortet
 * dann via EntryPoint mit 401. Der Filter selbst schreibt keine Fehlerantwort und blockiert den
 * Chain-Durchlauf nie.
 *
 * <p>Der Vergleich der {@code tokenVersion} (BE-AUTH-11, #201) braucht einen DB-Lookup pro
 * authentifiziertem Request — der Filter ist damit nicht mehr rein zustandslos, wie es ADR-7
 * ursprünglich vorsah. Das ist der bewusst akzeptierte Trade-off: nur damit macht eine
 * Passwort-Änderung zuvor ausgestellte Tokens tatsächlich ungültig, statt sie bis zum
 * natürlichen Ablauf gültig zu lassen.
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "jwt";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtCookieAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        readJwtCookie(request).ifPresent(token -> authenticate(token, request));
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            JwtService.TokenClaims claims = jwtService.validate(token);
            User user = userRepository.findById(claims.userId()).orElse(null);
            if (user == null || user.getTokenVersion() != claims.tokenVersion()) {
                // Unbekannter User oder Token stammt von vor einer Passwort-Änderung.
                SecurityContextHolder.clearContext();
                return;
            }
            // Ab hier trägt jede Log-Zeile dieses Threads die User-ID (INFRA-37). Aufgeräumt
            // wird sie im LoggingContextFilter, der diesen Filter umschliesst.
            LogContext.putUserId(claims.userId());
            var authentication =
                    new UsernamePasswordAuthenticationToken(claims.userId(), null, List.of());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException e) {
            // Ungültig/abgelaufen/manipuliert → nicht authentifizieren (EntryPoint liefert 401).
            SecurityContextHolder.clearContext();
        }
    }

    private Optional<String> readJwtCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }
}
