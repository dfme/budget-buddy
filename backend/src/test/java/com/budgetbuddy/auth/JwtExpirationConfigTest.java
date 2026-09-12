package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/**
 * Nagelt die ausgelieferte JWT-Laufzeit auf ihren beschlossenen Wert fest (BE-AUTH-13, #289).
 *
 * <p>Diese Lücke war der eigentliche Befund des Tasks. {@code JwtServiceTest.rejectsExpiredToken}
 * belegt, dass ein abgelaufenes Token abgewiesen wird — aber mit einer im Test selbst gebauten
 * Dauer von −1 s. Der Test ist damit konfigurationsunabhängig und bliebe grün, wenn jemand
 * {@code app.jwt.expiration} auf {@code 24h} zurückdrehte. Dasselbe gilt für
 * {@code JwtCookieFactoryTest}: der prüft die Spiegelung von Token-Laufzeit auf Cookie-maxAge,
 * nicht den Produktionswert. Vor diesem Test war die Verkürzung durch keinen einzigen Test
 * gedeckt und eine stille Rückkehr zum alten Fenster wäre unbemerkt durchgegangen.
 *
 * <p>Gebunden wird über {@link ConfigDataApplicationContextInitializer} die echte
 * {@code application.properties} — nicht eine im Test gesetzte Property, die nur sich selbst
 * bestätigen würde. Das nötige {@code JWT_SECRET} für die {@code @Validated}-Bindung liefert
 * Surefire (siehe {@code pom.xml}).
 */
class JwtExpirationConfigTest {

    /**
     * Der beschlossene Wert aus BE-AUTH-13. Bewusst als Literal und nicht aus der Konfiguration
     * gelesen: Ein Test, der seinen Erwartungswert von der geprüften Quelle bezieht, ist immer
     * grün und prüft nichts.
     */
    private static final Duration DECIDED_EXPIRATION = Duration.ofHours(4);

    @Test
    void configuredExpirationIsFourHours() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(JwtPropertiesConfiguration.class)
                .run(context -> assertThat(context.getBean(JwtProperties.class).expiration())
                        .as("app.jwt.expiration ist das Fenster, in dem ein abhandengekommenes "
                                + "Cookie nutzbar bleibt (BE-AUTH-13)")
                        .isEqualTo(DECIDED_EXPIRATION));
    }

    @Test
    void fallbackMatchesConfiguredExpiration() {
        // Fehlt die Property, greift der Default im Record-Konstruktor. Läuft der auseinander,
        // reisst er genau das Fenster wieder auf, das BE-AUTH-13 geschlossen hat — lautlos.
        JwtProperties withoutExpiration =
                new JwtProperties("unit-test-secret-long-enough-for-hs256-0123456789", null);

        assertThat(withoutExpiration.expiration())
                .as("der Fallback in JwtProperties darf nie grosszügiger sein als die Konfiguration")
                .isEqualTo(DECIDED_EXPIRATION);
    }

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class JwtPropertiesConfiguration {}
}
