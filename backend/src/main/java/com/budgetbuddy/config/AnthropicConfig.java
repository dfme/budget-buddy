package com.budgetbuddy.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.budgetbuddy.categorization.AnthropicProperties;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client-Konfiguration für die Anthropic-API (BE-CAT-02).
 *
 * <p>Der Client wird nur erzeugt, wenn ein API-Key konfiguriert ist. Fehlt er, existiert die Bean
 * nicht und {@code ClaudeCategorizationService} liefert den Fallback {@code Sonstiges} — die App
 * startet also auch ohne Anthropic-Account (siehe {@link AnthropicProperties}).
 */
@Configuration
public class AnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

    /**
     * Timeout pro HTTP-Request (BE-CAT-02, Acceptance Criteria).
     *
     * <p>Gilt pro Transaktion, nicht pro Import: Der Import ruft
     * {@code CategorizationPort.categorize} je unbekannter Transaktion einmal auf. Gegen die
     * Summe über alle Transaktionen schützt der Circuit Breaker im Service, nicht dieser Timeout.
     */
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /**
     * Ein Retry pro Request (SDK-Default wäre 2).
     *
     * <p>Nicht als Latenzschutz gedacht — das leistet der Circuit Breaker. Der Retry verhindert,
     * dass eine 429-Serie (Rate Limit) den Breaker fälschlich öffnet und damit den gesamten
     * restlichen Import auf {@code Sonstiges} kippt, obwohl die API gesund ist. Das SDK
     * respektiert bei 429 den {@code retry-after}-Header.
     */
    private static final int MAX_RETRIES = 1;

    /**
     * Anthropic-Client, oder {@code null}, wenn kein API-Key gesetzt ist.
     *
     * <p>Spring registriert bei {@code null} keine Bean; der Service injiziert sie deshalb via
     * {@code ObjectProvider} und kommt mit ihrer Abwesenheit klar.
     */
    @Bean
    public AnthropicClient anthropicClient(AnthropicProperties properties) {
        if (!properties.hasKey()) {
            log.warn(
                    "ANTHROPIC_API_KEY ist nicht gesetzt — Kategorisierung via Claude ist "
                            + "deaktiviert, unbekannte Transaktionen werden als 'Sonstiges' "
                            + "eingestuft.");
            return null;
        }

        log.info("Anthropic-Client initialisiert (Modell: {})", properties.model());
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.key())
                .timeout(TIMEOUT)
                .maxRetries(MAX_RETRIES)
                .build();
    }

    /** Systemzeit für den Circuit Breaker; in Tests durch eine feste Clock ersetzbar. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
