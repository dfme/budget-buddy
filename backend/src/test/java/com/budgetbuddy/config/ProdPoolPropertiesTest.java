package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariConfig;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

/**
 * Verifiziert, dass die Pool-Einstellungen aus {@code application-prod.properties} tatsächlich
 * auf HikariCP binden (INFRA-28).
 *
 * <p>Der Test existiert wegen eines stillen Fehlermodus: Unbekannte Schlüssel unter
 * {@code spring.datasource.hikari.*} werden beim Binden ignoriert, nicht abgelehnt. Ein Vertipper
 * liesse die App also normal starten — mit den Defaults {@code minimumIdle = maximumPoolSize = 10}
 * und damit genau dem Dauerverbrauch an Neon-Compute, den INFRA-28 abgestellt hat. Auffallen würde
 * das erst bei der nächsten Warnmail von Neon.
 *
 * <p>Ein Integrationstest gegen das Profil {@code prod} käme dafür nicht in Frage: Er würde eine
 * Verbindung zur produktiven Neon-Datenbank aufbauen wollen.
 */
class ProdPoolPropertiesTest {

    private static final String PREFIX = "spring.datasource.hikari";

    @Test
    void prodProfileShrinksThePoolToZeroWhenIdle() throws IOException {
        HikariConfig config = bindProdProperties();

        // Ohne Binding stünde hier der HikariCP-Default -1 ("so viele Idle-Verbindungen wie
        // maximumPoolSize"). 0 belegt also, dass der Schlüssel angekommen ist.
        assertThat(config.getMinimumIdle()).isZero();
    }

    @Test
    void prodProfileSetsIdleTimeout() throws IOException {
        HikariConfig config = bindProdProperties();

        assertThat(config.getIdleTimeout()).isEqualTo(60_000L);
        // Hikari deaktiviert idleTimeout stillschweigend, wenn er zu nah an maxLifetime liegt.
        assertThat(config.getIdleTimeout()).isLessThan(config.getMaxLifetime());
    }

    private HikariConfig bindProdProperties() throws IOException {
        Properties properties =
                PropertiesLoaderUtils.loadProperties(new ClassPathResource(
                        "application-prod.properties"));
        Map<String, Object> source = new HashMap<>();
        properties.forEach((key, value) -> source.put((String) key, value));

        HikariConfig config = new HikariConfig();
        new Binder(new MapConfigurationPropertySource(source))
                .bind(PREFIX, Bindable.ofInstance(config));
        return config;
    }
}
