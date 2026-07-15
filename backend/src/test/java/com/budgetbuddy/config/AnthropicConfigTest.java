package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.AnthropicClient;
import com.budgetbuddy.categorization.AnthropicProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifiziert beide Konfigurationspfade der {@link AnthropicConfig} (BE-CAT-02).
 *
 * <p>Der Pfad <em>mit</em> Key ist der Produktionspfad, wird aber von keinem anderen Test berührt:
 * In Test und CI ist nie ein {@code ANTHROPIC_API_KEY} gesetzt, alle übrigen Tests durchlaufen
 * also ausschliesslich den keylosen Fall. Ohne diesen Test würde ein Fehler in der
 * Client-Konstruktion erst beim Deployment auffallen.
 */
class AnthropicConfigTest {

    @Nested
    @SpringBootTest
    @TestPropertySource(properties = {"anthropic.api.key=", "SQLITE_DB_PATH=:memory:"})
    class WithoutApiKey {

        @Autowired private ObjectProvider<AnthropicClient> clientProvider;
        @Autowired private AnthropicProperties properties;

        /** Ohne Key muss die App normal starten — die Kategorisierung degradiert auf Sonstiges. */
        @Test
        void contextLoadsAndClientIsAbsent() {
            assertThat(properties.hasKey()).isFalse();
            assertThat(clientProvider.getIfAvailable()).isNull();
        }

        @Test
        void modelFallsBackToDefault() {
            assertThat(properties.model()).isEqualTo(AnthropicProperties.DEFAULT_MODEL);
        }
    }

    @Nested
    @SpringBootTest
    @TestPropertySource(
            properties = {"anthropic.api.key=sk-ant-test-key-not-real", "SQLITE_DB_PATH=:memory:"})
    class WithApiKey {

        @Autowired private ObjectProvider<AnthropicClient> clientProvider;
        @Autowired private AnthropicProperties properties;

        /**
         * Der Client wird gebaut, ohne dass ein API-Call stattfindet — der Konstruktor geht nicht
         * ins Netz, der Key wird erst beim ersten Request geprüft.
         */
        @Test
        void contextLoadsAndClientIsBuilt() {
            assertThat(properties.hasKey()).isTrue();
            assertThat(clientProvider.getIfAvailable()).isNotNull();
        }
    }

    /**
     * Verifiziert das in README.md dokumentierte Override via {@code ANTHROPIC_API_MODEL} — die
     * Doku soll nicht behaupten, was der Code nicht kann.
     */
    @Nested
    @SpringBootTest
    @TestPropertySource(
            properties = {"ANTHROPIC_API_MODEL=claude-sonnet-5", "SQLITE_DB_PATH=:memory:"})
    class WithModelOverride {

        @Autowired private AnthropicProperties properties;

        @Test
        void environmentVariableOverridesDefaultModel() {
            assertThat(properties.model()).isEqualTo("claude-sonnet-5");
        }
    }
}
