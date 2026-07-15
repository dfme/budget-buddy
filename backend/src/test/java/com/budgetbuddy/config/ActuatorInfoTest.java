package com.budgetbuddy.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifiziert den Actuator-Info-Endpoint (INFRA-08): Er meldet den ausgeführten Commit-SHA,
 * damit der CD-Smoke-Test prüfen kann, ob die neue Version deployt wurde — statt nur, ob
 * irgendeine (womöglich alte) Instanz antwortet.
 *
 * <p>Auf Render liefert die Default-Env-Var {@code RENDER_GIT_COMMIT} den Wert. Lokal und in
 * Tests fehlt sie, daher der Fallback {@code unknown} — der im CD nie auf einen GITHUB_SHA
 * matcht und den Job damit bewusst rot laufen lässt, statt still grün zu werden.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ActuatorInfoTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void infoEndpointExposesCommit() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.commit").exists());
    }

    @Test
    void commitFallsBackToUnknownWithoutRenderEnvVar() throws Exception {
        // RENDER_GIT_COMMIT ist in der Testumgebung nicht gesetzt: Der Platzhalter muss auf
        // "unknown" auflösen und nicht als unaufgelöstes "${RENDER_GIT_COMMIT}" durchschlagen.
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.commit").value("unknown"));
    }
}
