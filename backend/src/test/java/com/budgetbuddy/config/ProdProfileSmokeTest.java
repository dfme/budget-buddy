package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Happy-Path-Test für INFRA-04: verifiziert, dass der Spring-Kontext mit dem
 * Produktions-Profil ({@code application-prod.properties}) fehlerfrei startet.
 *
 * <p>Die Datenbank zeigt auf den Testcontainers-Postgres statt auf Neon: das prod-Profil
 * enthält seit DB-05 (ADR-12) selbst keine Verbindungsdaten mehr, die kommen in Produktion
 * ausschliesslich aus der Render-Umgebung. Der Test belegt damit, dass das Profil startfähig
 * ist — nicht, dass eine bestimmte Neon-Instanz erreichbar wäre.
 */
@SpringBootTest
@ActiveProfiles("prod")
class ProdProfileSmokeTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "prod_profile_smoke");
    }

    @Autowired
    private Environment environment;

    @Test
    void contextLoadsWithProdProfile() {
        assertThat(environment.getActiveProfiles()).contains("prod");
        // Prod-spezifische Überschreibungen greifen.
        assertThat(environment.getProperty("logging.level.com.budgetbuddy")).isEqualTo("INFO");
        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:postgresql://");
    }
}
