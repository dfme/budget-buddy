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

    /** Default-Gültigkeitsdauer, falls {@code app.jwt.expiration} nicht gesetzt ist. */
    public JwtProperties {
        if (expiration == null) {
            expiration = Duration.ofHours(24);
        }
    }
}
