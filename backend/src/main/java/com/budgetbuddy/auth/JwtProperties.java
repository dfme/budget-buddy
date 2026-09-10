package com.budgetbuddy.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Konfiguration für die JWT-Signierung (BE-AUTH-01, ADR-7).
 *
 * <p>Das Secret stammt ausschliesslich aus der Umgebungsvariable {@code JWT_SECRET}
 * (gemappt via {@code app.jwt.secret}) und wird nie hardcodiert. Fehlt oder ist es zu
 * kurz, schlägt die Validierung beim Start fehl → Fail-fast statt unsicherem Default.
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(

        @NotBlank(message = "JWT_SECRET muss als Umgebungsvariable gesetzt sein (app.jwt.secret).")
        @Size(min = 32, message = "JWT_SECRET muss für HS256 mindestens 32 Zeichen (256 Bit) lang sein.")
        String secret,

        Duration expiration) {

    /**
     * Default-Gültigkeitsdauer, falls {@code app.jwt.expiration} nicht gesetzt ist (BE-AUTH-13).
     *
     * <p>Der Wert muss dem in {@code application.properties} entsprechen. Er ist kein
     * beliebiger Notnagel: Weil der Token nach dem Login von nichts mehr erneuert wird, ist die
     * Gültigkeitsdauer exakt das Fenster, in dem ein abhandengekommenes Cookie nutzbar bleibt.
     * Ein Fallback, der grosszügiger ist als die konfigurierte Dauer, würde dieses Fenster
     * lautlos wieder aufreissen, sobald die Property einmal fehlt — genau der Zustand, den
     * BE-AUTH-13 abgeschafft hat. {@code JwtExpirationConfigTest} hält beide Werte gegeneinander.
     */
    public JwtProperties {
        if (expiration == null) {
            expiration = Duration.ofHours(4);
        }
    }
}
