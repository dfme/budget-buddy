package com.budgetbuddy.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt die Systemzeit als injizierbare {@link Clock} bereit.
 *
 * <p>Konsumenten (Circuit Breaker in {@code ClaudeCategorizationService}, Import-Deadline in
 * {@code PdfImportService}) hängen damit nicht an {@code Instant.now()} und sind in Tests mit
 * einer festen bzw. gesteuerten Clock deterministisch prüfbar.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
