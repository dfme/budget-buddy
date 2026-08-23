package com.budgetbuddy.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifiziert die stateless Security-Konfiguration (Schritt 3): geschützte Pfade liefern 401
 * ohne Basic-Auth-Prompt, öffentliche Pfade (Actuator-Health/-Info) bleiben erreichbar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "security_config");
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void protectedPathWithoutAuthReturns401() throws Exception {
        mockMvc.perform(get("/api/secured"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedPathDoesNotPromptForBasicAuth() throws Exception {
        // HttpStatusEntryPoint statt Basic: kein WWW-Authenticate-Header.
        mockMvc.perform(get("/api/secured"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void livenessProbeIsPublic() throws Exception {
        // Render pingt /actuator/health/liveness (render.yaml healthCheckPath, INFRA-28).
        // Ein 401 hier liesse den Service auf Render als unhealthy erscheinen und den Deploy
        // zurückrollen — PUBLIC_PATHS listet den Pfad deshalb exakt.
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk());
    }

    @Test
    void otherHealthGroupsStayProtected() throws Exception {
        // Gegenprobe zur Freigabe oben: Freigegeben ist der exakte Liveness-Pfad, nicht
        // /actuator/health/**. Die ebenfalls von probes.enabled erzeugte Readiness-Gruppe
        // bleibt damit hinter der Authentifizierung.
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void infoEndpointIsPublic() throws Exception {
        // Der CD-Smoke-Test liest den deployten Commit vor jedem Login (INFRA-08).
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }
}
