package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifiziert INFRA-05 (AC #3) und den Catch-all-Forward aus INFRA-17: die im JAR gebündelte
 * Angular-SPA wird ohne Authentifizierung ausgeliefert, während die REST-API unter
 * {@code /api/**} geschützt bleibt.
 *
 * <p>Die {@code index.html}/{@code main-test.js} unter {@code src/test/resources/static/} sind
 * Fixtures — im echten Prod-JAR liefert das {@code -Pprod}-Profil den Angular-Build (INFRA-04).
 *
 * <p><b>{@code RANDOM_PORT} statt {@code MockMvc} (INFRA-17, AC #4).</b> Ein reiner
 * Handler-Mapping-Test mit MockMvc würde nur beweisen, dass irgendein Handler existiert — nicht,
 * dass der ECHTE Servlet-Container dieselbe Route auflöst wie ein Browser. Genau diese Lücke
 * verpasste {@code PdfImportControllerIntegrationTest} beim ERROR-Dispatch (FE-PDF-02, siehe
 * {@link com.budgetbuddy.transaction.PdfImportErrorDispatchIntegrationTest}); für die
 * SPA-Routen ist das Risiko dasselbe, weil {@code SpaForwardController} jetzt einen
 * Catch-all-Pattern-Match gegen echte Infrastruktur-Handler (Actuator, Springdoc) abgrenzen muss
 * — eine Verwechslung wäre mit MockMvc, das keine echten Actuator-/Springdoc-Handler-Mappings
 * registriert, unsichtbar geblieben.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SpaRoutingTest {

    /** Marker aus der index.html-Fixture — beweist, dass tatsächlich die SPA-Shell ankam. */
    private static final String SPA_SHELL_MARKER = "spa-index-fixture";

    /** Marker aus der main-test.js-Fixture — beweist, dass das echte Asset ankam. */
    private static final String STATIC_ASSET_MARKER = "spaAssetFixture";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "spa_routing");
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void rootServesIndexHtmlWithoutAuth() {
        // AC #3: / ist öffentlich (kein 401) und liefert die gebündelte index.html.
        ResponseEntity<String> response = restTemplate.getForEntity("/", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains(SPA_SHELL_MARKER);
    }

    /**
     * Repräsentative Angular-Top-Level-Routen (deckungsgleich mit
     * {@code frontend/src/app/app.routes.ts}) — keine Enumeration mehr, die mit einer
     * Backend-Liste synchron gehalten werden muss (INFRA-17); dieser Test beweist nur, dass der
     * Catch-all-Mechanismus für jede von ihnen tatsächlich greift, inklusive
     * {@code /styleguide}, das vor INFRA-17 aus der alten Liste bewusst ausgeschlossen war.
     */
    @ParameterizedTest(name = "{0} → SPA-Shell")
    @ValueSource(strings = {
        "/dashboard", "/login", "/register", "/categories", "/import",
        "/onboarding", "/fixkosten", "/styleguide"
    })
    void spaDeepLinkForwardsToIndexHtml(String route) {
        // Hard-Reload/Deep-Link einer client-seitigen Route → SPA-Shell, ohne Auth.
        ResponseEntity<String> response = restTemplate.getForEntity(route, String.class);

        assertThat(response.getStatusCode().value())
                .as("%s muss die SPA-Shell liefern, nicht 401/404", route)
                .isEqualTo(200);
        assertThat(response.getBody()).contains(SPA_SHELL_MARKER);
    }

    @Test
    void nestedClientRouteForwardsToIndexHtml() {
        // Kind-Route ohne eigenen Eintrag in irgendeiner Liste — der Catch-all deckt Nesting
        // strukturell ab (vorher hätte das ein weiteres exaktes Pattern gebraucht, INFRA-17).
        ResponseEntity<String> response =
                restTemplate.getForEntity("/categories/lebensmittel", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains(SPA_SHELL_MARKER);
    }

    @Test
    void importApiIsNotShadowedBySpaForward() {
        // Regression-Guard: /api/import ist Frontend-Route-Namensvetter UND API-Prefix
        // (PdfImportController). Der geplante GET /api/import/{jobId}/status muss 401 bleiben,
        // nicht 200 mit der SPA-Shell — sonst wären Transaktionsdaten ohne Auth lesbar
        // (Risiko #2).
        ResponseEntity<String> response =
                restTemplate.getForEntity("/api/import/42/status", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void apiRemainsProtected() {
        // Regression-Guard: die SPA-Freigabe darf die geschützte API nicht öffnen.
        ResponseEntity<String> response = restTemplate.getForEntity("/api/users/me", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void staticAssetIsPubliclyServedAndNotShadowed() {
        // Gehashte JS/CSS-Bundles müssen ohne Auth ladbar sein UND ihren echten Inhalt liefern
        // — nicht versehentlich vom Catch-all auf die SPA-Shell umgeleitet werden.
        ResponseEntity<String> response = restTemplate.getForEntity("/main-test.js", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).contains(STATIC_ASSET_MARKER);
        assertThat(response.getBody()).doesNotContain(SPA_SHELL_MARKER);
    }

    @Test
    void actuatorHealthIsNotShadowedByCatchAll() {
        // Regression-Guard für INFRA-17: /actuator/health hat wie eine Angular-Kind-Route zwei
        // Segmente ohne Punkt — ohne den expliziten Ausschluss im SpaForwardController-Regex
        // hätte der Catch-all ihn geschluckt und die SPA-Shell statt echter Health-Daten
        // geliefert.
        ResponseEntity<String> response =
                restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).doesNotContain(SPA_SHELL_MARKER);
    }

    @Test
    void actuatorInfoIsNotShadowedByCatchAll() {
        // Der CD-Smoke-Test (INFRA-08) liest den Commit-SHA vor jedem Login — muss echtes JSON
        // bleiben, nicht die SPA-Shell.
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/info", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).doesNotContain(SPA_SHELL_MARKER);
    }

    @Test
    void openApiDocsAreNotShadowedByCatchAll() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).doesNotContain(SPA_SHELL_MARKER);
    }
}
