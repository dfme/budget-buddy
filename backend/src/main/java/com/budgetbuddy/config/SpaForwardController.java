package com.budgetbuddy.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Leitet client-seitige Angular-Routen auf {@code index.html} weiter (INFRA-05).
 *
 * <p>Die SPA nutzt HTML5-Pushstate-Routing. Beim Hard-Reload oder Deep-Link (z.B.
 * {@code /dashboard}) trifft die Anfrage zuerst den Server; ohne diese Weiterleitung gäbe
 * es ein 404, weil kein statisches File {@code /dashboard} existiert. Der {@code forward}
 * liefert stattdessen die SPA-Einstiegsseite aus, sodass der Angular-Router die Route
 * client-seitig auflöst. Es ist ein interner Servlet-Forward (keine Redirect-Antwort),
 * die URL im Browser bleibt erhalten.
 *
 * <p>{@code @Hidden}: kein REST-Endpoint — nicht in der OpenAPI-/Swagger-Doku führen.
 *
 * <p>{@link #CLIENT_ROUTE_PATTERNS} ist die einzige Liste der client-seitigen Routen;
 * {@code SecurityConfig} leitet seine GET-Freigabe daraus ab. Echte API-Pfade (z.B.
 * {@code /users/**}) dürfen hier nie gemappt werden.
 */
@Hidden
@Controller
public class SpaForwardController {

    /**
     * Client-seitige Angular-Routen, die per Deep-Link/Hard-Reload auf {@code index.html}
     * weitergeleitet und dafür öffentlich per GET erreichbar sein müssen.
     *
     * <p>Deckungsgleich zu {@code frontend/src/app/app.routes.ts}. {@code SpaRoutingTest} prüft
     * beides: dass jedes Pattern hier tatsächlich weiterleitet und dass die Liste identisch mit
     * dem {@code @GetMapping} unten ist.
     *
     * <p><b>Exakte Pfade, kein {@code /**}.</b> {@code /import} ist gleichzeitig Frontend-Route
     * UND API-Prefix ({@code PdfImportController}: {@code POST /import/pdf}, geplant
     * {@code GET /import/{jobId}/status}). Ein Wildcard-Pattern {@code /import/**} würde von
     * {@code SecurityConfig} als {@code permitAll} übernommen und den Status-Endpoint bei seiner
     * Einführung ohne Auth erreichbar machen — Transaktionsdaten öffentlich, Risiko #2. Exakte
     * Pfade schliessen das aus.
     *
     * <p>Bewusst NICHT enthalten: {@code /styleguide}. Die Route hängt am {@code devOnlyGuard}
     * und soll in Produktion nicht erreichbar sein.
     *
     * <p>Bei neuen Frontend-Routen hier ergänzen — auch bei Kind-Routen (z.B.
     * {@code /categories/lebensmittel}), die ein exaktes Pattern nicht mitabdeckt. Dann ein
     * eng gefasstes Pattern wählen ({@code /categories/*}), nie {@code /**} über einem
     * API-Prefix.
     */
    static final String[] CLIENT_ROUTE_PATTERNS = {
        "/dashboard", "/login", "/register", "/categories", "/import", "/onboarding"
    };

    /**
     * Die Patterns stehen hier als Literale, weil Annotation-Werte auf konstante Ausdrücke
     * beschränkt sind und ein Array-Feld keiner ist. {@code SpaRoutingTest} hält die beiden
     * Listen per Assertion zusammen.
     */
    @GetMapping({"/dashboard", "/login", "/register", "/categories", "/import", "/onboarding"})
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
