package com.budgetbuddy.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Spannt den Logging-Kontext eines Requests auf und räumt ihn wieder ab (INFRA-37).
 *
 * <p>Der Filter vergibt die {@code requestId} und ist die einzige Stelle, die den MDC am Ende
 * eines Requests leert. Die {@code userId} steuert
 * {@code JwtCookieAuthenticationFilter} bei — sie steht erst nach der Token-Validierung fest.
 *
 * <p><strong>Warum ein eigener Filter</strong> und nicht ein paar Zeilen im JWT-Filter: Das
 * {@code finally} muss die <em>ganze</em> Kette umschliessen, den JWT-Filter eingeschlossen.
 * Deshalb sitzt dieser Filter davor. Dass er auch unauthentifizierte Requests mit einer
 * Request-ID versieht, ist erwünscht — gerade fehlgeschlagene Logins will man im Log
 * zusammenhängend lesen können.
 *
 * <p><strong>Bekannte Grenze:</strong> {@link OncePerRequestFilter} überspringt den
 * ERROR-Dispatch (Default von {@code shouldNotFilterErrorDispatch}). Zeilen, die Springs
 * {@code /error}-Weiterleitung erzeugt, tragen deshalb keinen MDC. Das ist der Preis dafür, den
 * Kontext eines Requests nicht versehentlich in einen zweiten Dispatch zu verlängern; die
 * Fehlerbehandlung der Anwendung selbst läuft in {@code @ControllerAdvice} innerhalb der Kette
 * und ist abgedeckt.
 */
public class LoggingContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        LogContext.newRequestId();
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Auch im Fehlerfall: Tomcat gibt den Thread an den nächsten Request weiter, und der
            // erbte sonst die User-ID seines Vorgängers.
            LogContext.clear();
        }
    }
}
