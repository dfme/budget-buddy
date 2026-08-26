package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifiziert die Liveness-Probe, die Render im Dauerbetrieb pingt (INFRA-28).
 *
 * <p>Die Probe existiert aus einem Kostengrund: Am DB-behafteten {@code /actuator/health} hängt
 * der {@code DataSourceHealthIndicator}, der pro Aufruf ein {@code SELECT 1} absetzt. Seit der
 * Render-Service always-on läuft (INFRA-24), hielt dieser Ping Neons Compute rund um die Uhr wach
 * — rund 6 CU-h pro Tag des 100-CU-h-Monatskontingents, ohne dass ein Nutzer die App anfasste.
 * {@code /actuator/health/liveness} enthält ausschliesslich den {@code livenessState} und fasst
 * die Datenbank nicht an.
 *
 * <p>{@code management.endpoint.health.show-details=always} steht hier nur für
 * {@link #defaultHealthEndpointStillReportsDatabase()}: Der Kontrast zwischen beiden Endpoints
 * ist der eigentliche Entscheid — {@code /actuator/health} behält den DB-Status für Menschen und
 * den CD-Smoke-Test, nur der Plattform-Ping verliert ihn.
 */
@SpringBootTest(properties = "management.endpoint.health.show-details=always")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorLivenessProbeTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "actuator_liveness");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private HealthEndpointGroups healthEndpointGroups;

    @Test
    void livenessProbeIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void livenessProbeExcludesTheDatabase() {
        // Geprüft wird die Gruppenzugehörigkeit, nicht der Response-Body: Springs
        // AvailabilityProbesHealthEndpointGroup gibt in showComponents()/showDetails() hart
        // false zurück, unabhängig von show-details. Ein Body-Assert wäre also auch dann grün,
        // wenn der DB-Indikator in der Gruppe steckte — und würde genau den Fehler durchlassen,
        // den dieser Test verhindern soll.
        var liveness = healthEndpointGroups.get("liveness");

        assertThat(liveness).isNotNull();
        assertThat(liveness.isMember("livenessState")).isTrue();
        assertThat(liveness.isMember("db")).isFalse();
    }

    @Test
    void defaultHealthEndpointStillReportsDatabase() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }
}
