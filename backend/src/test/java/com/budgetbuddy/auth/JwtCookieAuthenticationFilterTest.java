package com.budgetbuddy.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-End-Test des JWT-Cookie-Filters über die echte SecurityFilterChain (Schritt 4):
 * gültiges Cookie → 200 + User-ID im SecurityContext, ungültiges/abgelaufenes/fehlendes
 * Cookie → 401. Deckt damit alle Acceptance Criteria von BE-AUTH-01 ab.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(JwtCookieAuthenticationFilterTest.TestController.class)
class JwtCookieAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Test
    void validCookieGrantsAccessAndExposesUserId() throws Exception {
        String token = jwtService.generateToken(99L);

        mockMvc.perform(get("/test/me").cookie(new Cookie("jwt", token)))
                .andExpect(status().isOk())
                .andExpect(content().string("99"));
    }

    @Test
    void invalidCookieReturns401() throws Exception {
        mockMvc.perform(get("/test/me").cookie(new Cookie("jwt", "not-a-valid-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredCookieReturns401() throws Exception {
        // Gleiches Secret wie der Kontext, aber sofort abgelaufen → gültige Signatur, exp in der
        // Vergangenheit → ExpiredJwtException im Filter → 401.
        JwtService expiredIssuer =
                new JwtService(new JwtProperties(jwtProperties.secret(), Duration.ofSeconds(-1)));
        String expired = expiredIssuer.generateToken(99L);

        mockMvc.perform(get("/test/me").cookie(new Cookie("jwt", expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noCookieReturns401() throws Exception {
        mockMvc.perform(get("/test/me"))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestController {

        @GetMapping("/test/me")
        String me(Authentication authentication) {
            return authentication.getPrincipal().toString();
        }
    }
}
