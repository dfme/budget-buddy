package com.budgetbuddy.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifiziert INFRA-05 (AC #3): die im JAR gebündelte Angular-SPA wird ohne Authentifizierung
 * ausgeliefert, während die REST-API geschützt bleibt.
 *
 * <p>Die {@code index.html}/{@code main-test.js} unter {@code src/test/resources/static/} sind
 * Fixtures — im echten Prod-JAR liefert das {@code -Pprod}-Profil den Angular-Build (INFRA-04).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaRoutingTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootServesIndexHtmlWithoutAuth() throws Exception {
        // AC #3: / ist öffentlich (kein 401) und wird von der Spring-Boot-Welcome-Page auf die
        // gebündelte index.html weitergeleitet (interner Forward, daher forwardedUrl statt Body).
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void spaDeepLinkForwardsToIndexHtml() throws Exception {
        // Hard-Reload/Deep-Link einer client-seitigen Route → forward auf index.html.
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void staticAssetIsPubliclyServed() throws Exception {
        // Gehashte JS/CSS-Bundles müssen ohne Auth ladbar sein, sonst startet die SPA nicht.
        mockMvc.perform(get("/main-test.js"))
                .andExpect(status().isOk());
    }

    @Test
    void apiRemainsProtected() throws Exception {
        // Regression-Guard: die SPA-Freigabe darf die geschützte API nicht öffnen.
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }
}
