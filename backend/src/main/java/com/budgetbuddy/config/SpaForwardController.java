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
 * <p>Bei neuen Frontend-Routen: Muster hier UND in {@code SecurityConfig#SPA_GET_PATHS}
 * ergänzen. Echte API-Pfade (z.B. {@code /users/**}) dürfen hier nie gemappt werden.
 */
@Hidden
@Controller
public class SpaForwardController {

    @GetMapping({"/dashboard/**", "/login/**"})
    public String forwardToSpa() {
        return "forward:/index.html";
    }
}
