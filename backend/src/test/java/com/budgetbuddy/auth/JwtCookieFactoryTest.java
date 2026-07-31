package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

/**
 * Deckt die Cookie-Attribute aus ADR-7 ab, insbesondere das {@code Secure}-Flag.
 *
 * <p>{@code HttpOnly} und {@code SameSite=Strict} prüft bereits {@code AuthControllerTest} am
 * {@code Set-Cookie}-Header. {@code Secure} war dort nicht abgedeckt und liess sich dort auch
 * nicht abdecken: es ist über {@code app.cookie.secure} konfiguriert und steht im Test- wie im
 * Dev-Profil auf {@code false} (HTTP-localhost). Auch die E2E-Tests können es nicht prüfen — ein
 * {@code Secure}-Cookie über {@code http://localhost} würde der Browser gar nicht erst speichern.
 *
 * <p>Damit war die Zusicherung „das JWT-Cookie verlässt in Produktion nie eine unverschlüsselte
 * Verbindung" bislang durch keinen einzigen Test gedeckt — ein Flip von
 * {@code app.cookie.secure} in {@code application-prod.properties} wäre lautlos durchgegangen.
 * Dieser Test prüft die Factory direkt mit beiden Konfigurationswerten und schliesst die Lücke.
 */
class JwtCookieFactoryTest {

    private static final Duration EXPIRATION = Duration.ofHours(24);

    // Mindestens 32 Zeichen (JwtProperties); der Wert wird hier nie zum Signieren benutzt.
    private static final JwtProperties PROPERTIES =
            new JwtProperties("test-secret-fuer-cookie-attribute-nur", EXPIRATION);

    @Test
    void productionCookieIsSecure() {
        // Produktionsnahe Konfiguration (application-prod.properties: app.cookie.secure=true).
        ResponseCookie cookie = new JwtCookieFactory(true, PROPERTIES).create("token");

        assertThat(cookie.isSecure())
                .as("JWT-Cookie darf in Produktion nur über HTTPS gesendet werden (ADR-7)")
                .isTrue();
    }

    @Test
    void devCookieIsNotSecure() {
        // Dev/Test: ohne dieses Zugeständnis wäre über HTTP-localhost kein Login möglich.
        ResponseCookie cookie = new JwtCookieFactory(false, PROPERTIES).create("token");

        assertThat(cookie.isSecure()).isFalse();
    }

    @Test
    void cookieCarriesXssAndCsrfAttributes() {
        ResponseCookie cookie = new JwtCookieFactory(true, PROPERTIES).create("token");

        assertThat(cookie.getName()).isEqualTo("jwt");
        assertThat(cookie.getValue()).isEqualTo("token");
        assertThat(cookie.isHttpOnly()).as("kein JS-Zugriff → XSS-Schutz").isTrue();
        assertThat(cookie.getSameSite()).as("CSRF-Schutz statt CSRF-Token").isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
        assertThat(cookie.getMaxAge()).isEqualTo(EXPIRATION);
    }

    @Test
    void clearCookieKeepsAttributesAndExpiresImmediately() {
        // Logout: Max-Age=0 invalidiert sofort. Die übrigen Attribute müssen identisch bleiben,
        // sonst adressiert der Browser ein anderes Cookie und das alte überlebt den Logout.
        ResponseCookie cookie = new JwtCookieFactory(true, PROPERTIES).clear();

        assertThat(cookie.getMaxAge()).isZero();
        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo("Strict");
        assertThat(cookie.getPath()).isEqualTo("/");
    }
}
