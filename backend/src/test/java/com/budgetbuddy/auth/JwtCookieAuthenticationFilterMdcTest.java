package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.budgetbuddy.config.LogContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Der Filter schreibt die User-ID in den MDC — aber nur, wenn das Token wirklich gültig war
 * (INFRA-37).
 *
 * <p>Der zweite Testfall ist der sicherheitsrelevante: Eine User-ID an Log-Zeilen eines
 * <em>nicht</em> authentifizierten Requests schriebe eine Behauptung ins Log, die der Request nie
 * belegt hat — und genau solche Zeilen liest man später bei einem Vorfall.
 */
@ExtendWith(MockitoExtension.class)
class JwtCookieAuthenticationFilterMdcTest {

    private static final String SECRET = "unit-test-secret-long-enough-for-hs256-0123456789";

    private final JwtService jwtService =
            new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    private JwtCookieAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        filter = new JwtCookieAuthenticationFilter(jwtService, userRepository);
    }

    @AfterEach
    void tearDown() {
        // Der Filter räumt bewusst nicht selbst auf — das tut der LoggingContextFilter, der ihn
        // in der echten Kette umschliesst (siehe LoggingContextFilterTest).
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenPutsUserIdIntoTheLogContext() throws Exception {
        when(userRepository.findById(99L)).thenReturn(Optional.of(user));
        when(user.getTokenVersion()).thenReturn(0L);

        Map<String, String> seenInsideChain = doFilterWithCookie(jwtService.generateToken(99L));

        assertThat(seenInsideChain)
                .as("ab dem Filter trägt jede Log-Zeile des Requests die User-ID")
                .containsEntry(LogContext.USER_ID, "99");
    }

    @Test
    void invalidTokenLeavesTheLogContextWithoutUserId() throws Exception {
        Map<String, String> seenInsideChain = doFilterWithCookie("not-a-valid-jwt");

        assertThat(seenInsideChain)
                .as("ohne gültiges Token wird keine User-ID behauptet")
                .doesNotContainKey(LogContext.USER_ID);
    }

    @Test
    void withoutCookieNoUserIdIsSet() throws Exception {
        Map<String, String> seenInsideChain = new HashMap<>();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                capturing(seenInsideChain));

        assertThat(seenInsideChain).doesNotContainKey(LogContext.USER_ID);
    }

    private Map<String, String> doFilterWithCookie(String token) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("jwt", token));
        Map<String, String> seenInsideChain = new HashMap<>();

        filter.doFilter(request, new MockHttpServletResponse(), capturing(seenInsideChain));

        return seenInsideChain;
    }

    /** Kette, die den MDC-Stand zum Zeitpunkt ihres Laufs festhält. */
    private static FilterChain capturing(Map<String, String> target) {
        return (request, response) -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            if (context != null) {
                target.putAll(context);
            }
        };
    }
}
