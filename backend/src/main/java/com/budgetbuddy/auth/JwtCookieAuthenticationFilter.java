package com.budgetbuddy.auth;

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
 * <p>Bei fehlendem, ungültigem oder abgelaufenem Token bleibt der SecurityContext leer; die
 * Autorisierung in {@code SecurityConfig} antwortet dann via EntryPoint mit 401. Der Filter
 * selbst schreibt keine Fehlerantwort und blockiert den Chain-Durchlauf nie.
 */
public class JwtCookieAuthenticationFilter extends OncePerRequestFilter {

    static final String COOKIE_NAME = "jwt";

    private final JwtService jwtService;

    public JwtCookieAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
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
            long userId = jwtService.validateAndGetUserId(token);
            var authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, List.of());
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
