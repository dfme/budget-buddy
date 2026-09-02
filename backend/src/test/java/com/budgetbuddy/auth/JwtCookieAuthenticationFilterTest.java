package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.budgetbuddy.config.LogContext;
import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.support.ThreadScopedLogAppender;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-End-Test des JWT-Cookie-Filters über die echte SecurityFilterChain (Schritt 4):
 * gültiges Cookie → 200 + User-ID im SecurityContext, ungültiges/abgelaufenes/fehlendes
 * Cookie → 401. Deckt damit alle Acceptance Criteria von BE-AUTH-01 ab.
 *
 * <p>Seit INFRA-37 zusätzlich der Logging-Kontext: Der Test-Controller loggt eine Zeile, ohne die
 * User-ID zu übergeben — sie muss trotzdem am Log-Event hängen. Das ist die Zusage von AC-1, und
 * sie lässt sich nur hier prüfen, wo beide Filter in ihrer echten Reihenfolge stehen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JwtCookieAuthenticationFilterTest.TestController.class)
class JwtCookieAuthenticationFilterTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "jwt_cookie_filter");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    private final Logger controllerLogger = (Logger) LoggerFactory.getLogger(TestController.class);
    private ThreadScopedLogAppender appender;

    @BeforeEach
    void attachAppender() {
        // MockMvc führt die Filterkette auf dem Testthread aus — der ThreadScopedLogAppender
        // sieht die Zeile deshalb, und zugleich keine Zeilen fremder Threads (#162).
        appender = new ThreadScopedLogAppender();
        appender.start();
        controllerLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        controllerLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void validCookieGrantsAccessAndExposesUserId() throws Exception {
        String token = jwtService.generateToken(99L);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", token)))
                .andExpect(status().isOk())
                .andExpect(content().string("99"));
    }

    @Test
    void invalidCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", "not-a-valid-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredCookieReturns401() throws Exception {
        // Gleiches Secret wie der Kontext, aber sofort abgelaufen → gültige Signatur, exp in der
        // Vergangenheit → ExpiredJwtException im Filter → 401.
        JwtService expiredIssuer =
                new JwtService(new JwtProperties(jwtProperties.secret(), Duration.ofSeconds(-1)));
        String expired = expiredIssuer.generateToken(99L);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logLineOfAnAuthenticatedRequestCarriesUserIdWithoutBeingPassed() throws Exception {
        String token = jwtService.generateToken(99L);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", token)))
                .andExpect(status().isOk());

        ILoggingEvent event = appender.list.stream()
                .filter(e -> e.getFormattedMessage().equals(TestController.LOG_MESSAGE))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Der Controller hat nichts geloggt"));

        assertThat(event.getFormattedMessage())
                .as("die aufrufende Stelle übergibt die User-ID nicht")
                .doesNotContain("99");
        assertThat(event.getMDCPropertyMap())
                .as("sie kommt aus dem MDC")
                .containsEntry(LogContext.USER_ID, "99");
        assertThat(event.getMDCPropertyMap())
                .containsKey(LogContext.REQUEST_ID);
    }

    @Test
    void contextIsClearedAfterTheRequest() throws Exception {
        String token = jwtService.generateToken(99L);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", token)))
                .andExpect(status().isOk());

        // Derselbe Thread bedient in Tomcat gleich den nächsten Request.
        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @RestController
    static class TestController {

        static final String LOG_MESSAGE = "Testendpunkt aufgerufen";

        private static final org.slf4j.Logger log = LoggerFactory.getLogger(TestController.class);

        @GetMapping("/api/test/me")
        String me(Authentication authentication) {
            log.info(LOG_MESSAGE);
            return authentication.getPrincipal().toString();
        }
    }
}
