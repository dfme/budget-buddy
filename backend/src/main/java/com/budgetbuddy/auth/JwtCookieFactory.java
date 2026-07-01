package com.budgetbuddy.auth;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Baut das {@code jwt}-Cookie für Login/Register (Set) und Logout (Clear) (BE-AUTH-03, ADR-7).
 *
 * <p>Es wird {@link ResponseCookie} statt {@code jakarta.servlet.http.Cookie} verwendet, weil nur
 * diese {@code SameSite=Strict} setzen kann (CSRF-Schutz statt CSRF-Token, vgl. ADR-7). Das Cookie
 * ist {@code HttpOnly} (kein JS-Zugriff → XSS-Schutz) und {@code Path=/}. Das {@code Secure}-Flag
 * ist via {@code app.cookie.secure} konfigurierbar: {@code false} im Dev (HTTP localhost),
 * {@code true} in Produktion (HTTPS). Die Lebensdauer entspricht der JWT-Gültigkeit.
 */
@Component
public class JwtCookieFactory {

    static final String COOKIE_NAME = JwtCookieAuthenticationFilter.COOKIE_NAME;

    private final boolean secure;
    private final Duration maxAge;

    public JwtCookieFactory(
            @Value("${app.cookie.secure:false}") boolean secure, JwtProperties jwtProperties) {
        this.secure = secure;
        this.maxAge = jwtProperties.expiration();
    }

    /** Set-Cookie mit dem signierten JWT; gültig für die Dauer der Token-Expiration. */
    public ResponseCookie create(String token) {
        return baseCookie(token).maxAge(maxAge).build();
    }

    /** Clear-Cookie ({@code Max-Age=0}) — invalidiert das JWT-Cookie sofort (Logout). */
    public ResponseCookie clear() {
        return baseCookie("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .sameSite("Strict");
    }
}
