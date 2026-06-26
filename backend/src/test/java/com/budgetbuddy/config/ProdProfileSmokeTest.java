package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Happy-Path-Test für INFRA-04: verifiziert, dass der Spring-Kontext mit dem
 * Produktions-Profil ({@code application-prod.properties}) fehlerfrei startet.
 *
 * <p>Die SQLite-DB wird auf {@code :memory:} gezwungen, damit der Test keine
 * Datei auf das Filesystem schreibt und unabhängig von der Render-Umgebung läuft.
 */
@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = "SQLITE_DB_PATH=:memory:")
class ProdProfileSmokeTest {

    @Autowired
    private Environment environment;

    @Test
    void contextLoadsWithProdProfile() {
        assertThat(environment.getActiveProfiles()).contains("prod");
        // Prod-spezifische Überschreibungen greifen.
        assertThat(environment.getProperty("logging.level.com.budgetbuddy")).isEqualTo("INFO");
        assertThat(environment.getProperty("spring.datasource.url")).contains(":memory:");
    }
}
