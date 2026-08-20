package com.budgetbuddy.config;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Leitet client-seitige Angular-Routen auf {@code index.html} weiter (INFRA-05, INFRA-17).
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
 * <p><b>Catch-all statt Enumeration (INFRA-17).</b> Seit alle REST-Endpoints unter
 * {@code /api/**} liegen ({@code SecurityConfig}), muss diese Klasse Angular-Routen nicht mehr
 * einzeln kennen: jede GET-Anfrage ausserhalb von {@code /api}, den Infrastruktur-Pfaden
 * ({@code actuator}, {@code error}, {@code v3}, {@code swagger-ui}) und den statischen Assets
 * ({@code assets/**} sowie jedes Segment mit Dateiendung, z.B. {@code main.js}) ist eine
 * client-seitige Route. Vorher mussten diese Klasse und {@code SecurityConfig} dieselbe
 * Routen-Liste unabhängig pflegen; das lief zweimal auseinander (INFRA-14, #126) — insbesondere
 * bei {@code /register}, {@code /categories} und {@code /import}, die im Angular-Router standen,
 * aber in keiner der beiden Backend-Listen. Die Trennung von API und SPA läuft jetzt strukturell
 * über den Pfad-Präfix statt über zwei von Hand synchron gehaltene Listen.
 *
 * <p>Zwei Patterns, weil eine Angular-Kind-Route wie {@code /categories/lebensmittel} mehr als
 * ein Segment hat und Spring-Pfadvariablen nicht über Segmentgrenzen hinweg matchen:
 *
 * <ul>
 *   <li>{@link #NOT_INFRA_SEGMENT} allein: genau ein Segment, z.B. {@code /dashboard}
 *   <li>{@link #NOT_INFRA_SEGMENT} + {@code /**}: erstes Segment plus beliebig viele weitere,
 *       z.B. {@code /categories/lebensmittel}
 * </ul>
 *
 * <p>Die Ausschlussliste im Regex wächst nur bei einem neuen Infrastruktur-Pfad ausserhalb von
 * {@code /api} — nicht bei jeder neuen Frontend-Route.
 */
@Hidden
@Controller
public class SpaForwardController {

    /**
     * Regex für ein einzelnes Pfadsegment, das weder ein bekannter Nicht-SPA-Pfad noch ein
     * Dateiname mit Endung ist. Muss als String-Konstante ausgedrückt sein — Annotation-Werte
     * sind auf konstante Ausdrücke beschränkt, kein dynamischer Aufbau zur Laufzeit.
     */
    private static final String NOT_INFRA_SEGMENT =
            "^(?!api|actuator|error|v3|swagger-ui|assets)[^.]*$";

    @GetMapping({
        "/{path:" + NOT_INFRA_SEGMENT + "}",
        "/{path:" + NOT_INFRA_SEGMENT + "}/**"
    })
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
