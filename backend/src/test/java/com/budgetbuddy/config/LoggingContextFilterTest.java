package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Deckt die beiden Zusagen des Filters ab (INFRA-37): Während der Kette steht der Kontext, danach
 * ist er weg — auch wenn die Kette mit einer Exception endet.
 *
 * <p>Der zweite Teil ist der wichtigere. Tomcat gibt seine Threads an den nächsten Request weiter;
 * ein nicht geleerter MDC schriebe die User-ID des einen Nutzers an die Log-Zeilen des nächsten
 * und machte die Logs damit schlechter als ganz ohne MDC.
 */
class LoggingContextFilterTest {

    private final LoggingContextFilter filter = new LoggingContextFilter();

    @AfterEach
    void clearMdc() {
        // Falls eine Assertion vor dem Ende fehlschlägt: kein Übertrag in den nächsten Testfall.
        MDC.clear();
    }

    @Test
    void setsRequestIdForTheDurationOfTheChain() throws Exception {
        Map<String, String> seenInsideChain = new HashMap<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturing(seenInsideChain));

        assertThat(seenInsideChain.get(LogContext.REQUEST_ID))
                .as("requestId steht der ganzen Kette zur Verfügung")
                .isNotBlank();
        assertThat(MDC.getCopyOfContextMap())
                .as("nach dem Request ist der Kontext leer")
                .isNullOrEmpty();
    }

    @Test
    void requestIdIsNewForEachRequest() throws Exception {
        Map<String, String> first = new HashMap<>();
        Map<String, String> second = new HashMap<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturing(first));
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturing(second));

        assertThat(first.get(LogContext.REQUEST_ID))
                .as("zwei Requests dürfen im Log nicht dieselbe ID tragen")
                .isNotEqualTo(second.get(LogContext.REQUEST_ID));
    }

    @Test
    void clearsContextWhenTheChainThrows() {
        // Der Fehlerpfad ist der, den ein finally-loser Filter überlebt hätte: Die Exception
        // verlässt den Filter, der Thread wird recycelt — mit fremdem Kontext.
        FilterChain failing = (request, response) -> {
            LogContext.putUserId(42L);
            throw new ServletException("Fehler in der Kette");
        };

        assertThatThrownBy(() -> filter.doFilter(
                        new MockHttpServletRequest(), new MockHttpServletResponse(), failing))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.getCopyOfContextMap())
                .as("auch nach einer Exception bleibt nichts stehen")
                .isNullOrEmpty();
    }

    @Test
    void doesNotLeakUserIdIntoTheNextRequestOnTheSameThread() throws Exception {
        Map<String, String> secondRequest = new HashMap<>();

        // Erster Request: authentifiziert (das täte sonst der JwtCookieAuthenticationFilter).
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> LogContext.putUserId(7L));
        // Zweiter Request auf demselben Thread: unauthentifiziert.
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturing(secondRequest));

        assertThat(secondRequest)
                .as("die User-ID des Vorgängers darf hier nicht mehr stehen")
                .doesNotContainKey(LogContext.USER_ID);
    }

    /** Kette, die den MDC-Stand zum Zeitpunkt ihres Laufs in {@code target} kopiert. */
    private static FilterChain capturing(Map<String, String> target) {
        return (request, response) -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            if (context != null) {
                target.putAll(context);
            }
        };
    }
}
