package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Verifiziert INFRA-05 (AC #3): die im JAR gebündelte Angular-SPA wird ohne Authentifizierung
 * ausgeliefert, während die REST-API geschützt bleibt.
 *
 * <p>Die {@code index.html}/{@code main-test.js} unter {@code src/test/resources/static/} sind
 * Fixtures — im echten Prod-JAR liefert das {@code -Pprod}-Profil den Angular-Build (INFRA-04).
 *
 * <p>Der Deep-Link-Test läuft parametrisiert über
 * {@link SpaForwardController#CLIENT_ROUTE_PATTERNS} statt über eine eigene Liste (INFRA-14):
 * eine hartkodierte Testliste hätte die 401-Lücke bei {@code /register}, {@code /categories}
 * und {@code /import} nicht gefunden, weil sie genau wie die Produktionslisten unvollständig
 * gewesen wäre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaRoutingTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "spa_routing");
    }

    /** Quelle für {@link #spaDeepLinkForwardsToIndexHtml} — siehe Klassen-Javadoc. */
    static final String[] CLIENT_ROUTES = SpaForwardController.CLIENT_ROUTE_PATTERNS;

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

    @ParameterizedTest(name = "{0} → index.html")
    @FieldSource("CLIENT_ROUTES")
    void spaDeepLinkForwardsToIndexHtml(String route) throws Exception {
        // Hard-Reload/Deep-Link einer client-seitigen Route → forward auf index.html, ohne Auth.
        // Ein 401 hier bedeutet: die Route fehlt in SecurityConfig; ein 404: sie fehlt im
        // @GetMapping des SpaForwardController.
        mockMvc.perform(get(route)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void importApiIsNotShadowedBySpaForward() throws Exception {
        // Regression-Guard für die Kollision, die CLIENT_ROUTE_PATTERNS exakt hält: /import ist
        // Frontend-Route UND API-Prefix. Mit /import/** wäre dieser Pfad permitAll und der
        // geplante GET /import/{jobId}/status ohne Auth lesbar (Risiko #2). 401 ist hier das
        // richtige Ergebnis — nicht 200 mit index.html.
        mockMvc.perform(get("/import/42/status")).andExpect(status().isUnauthorized());
    }

    @Test
    void mappingMatchesRoutePatterns() throws Exception {
        // CLIENT_ROUTE_PATTERNS und das @GetMapping des Controllers sind zwangsläufig zwei
        // Literal-Listen (Annotation-Werte müssen konstante Ausdrücke sein). Diese Assertion
        // verhindert, dass sie auseinanderlaufen — genau das war die Ursache der 401-Lücke.
        GetMapping mapping = SpaForwardController.class
                .getDeclaredMethod("forwardToSpa")
                .getAnnotation(GetMapping.class);

        assertThat(mapping.value())
                .as("@GetMapping muss deckungsgleich mit CLIENT_ROUTE_PATTERNS sein")
                .containsExactly(SpaForwardController.CLIENT_ROUTE_PATTERNS);
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
