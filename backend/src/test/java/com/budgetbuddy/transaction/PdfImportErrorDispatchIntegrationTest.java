package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.Category;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;

/**
 * Prüft über einen echten Servlet-Container, dass die Fehlerstatus des PDF-Imports den Client
 * tatsächlich erreichen (Review zu FE-PDF-02, PR #118).
 *
 * <p>Hintergrund: {@code @ResponseStatus} auf einer Exception ({@link PdfImportTimeoutException},
 * {@link DuplicatePdfImportException}) läuft über {@code response.sendError()} und damit über
 * Springs ERROR-Dispatch auf {@code /error}. Der {@code JwtCookieAuthenticationFilter} überspringt
 * den ERROR-Dispatch ({@code OncePerRequestFilter}-Default), der SecurityContext ist dort leer —
 * ohne {@code /error}-Freigabe in {@code SecurityConfig} überschrieb
 * {@code anyRequest().authenticated()} den echten Status mit 401 und der {@code
 * authErrorInterceptor} loggte den Nutzer mitten im Import aus.
 *
 * <p><b>Deshalb bewusst kein MockMvc:</b> MockMvc führt den ERROR-Dispatch nicht aus. {@code
 * PdfImportControllerIntegrationTest#duplicatePdfReturns409} war grün, während der Endpoint real
 * 401 lieferte — genau diese Lücke schliesst nur ein Test mit {@code RANDOM_PORT} +
 * {@link TestRestTemplate}.
 *
 * <p>{@code timeout-seconds=0} macht den 408 deterministisch: die Deadline ist direkt nach dem
 * Parsen überschritten, kein echtes Warten nötig.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "budgetbuddy.import.timeout-seconds=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportErrorDispatchIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "pdf_import_error_dispatch");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** Ersetzt die Hybrid-Kette (den {@code @Primary}-{@link CategorizationPort}) im Kontext. */
    @MockitoBean(name = "hybridCategorizationService")
    private CategorizationPort categorizationPort;

    private long userId;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "lara@example.ch", "bcrypt-hash", new BigDecimal("2200.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'lara@example.ch'", Long.class);
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(Category.LEBENSMITTEL));
    }

    private HttpHeaders authenticatedMultipartHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, "jwt=" + jwtService.generateToken(userId));
        return headers;
    }

    private static byte[] fixture() {
        try (InputStream in = PdfImportErrorDispatchIntegrationTest.class
                .getResourceAsStream("/pdf/UBS_Konto_Bewegungen_2021_Juli.pdf")) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void timeoutReachesClientAs408() {
        LinkedMultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fixture()) {
            @Override
            public String getFilename() {
                return "kontoauszug.pdf";
            }
        });

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/import/pdf", new HttpEntity<>(body, authenticatedMultipartHeaders()),
                String.class);

        // Vor der /error-Freigabe kam hier 401 an — der authErrorInterceptor hätte den Nutzer
        // ausgeloggt, statt die Timeout-Meldung zu zeigen (AC «408 Timeout» von FE-PDF-02).
        assertThat(response.getStatusCode().value())
                .as("408 muss den ERROR-Dispatch überleben, nicht als 401 ankommen")
                .isEqualTo(408);
    }

    @Test
    void missingFilePartReachesClientAs400() {
        // Auch Springs eigener 400 (fehlender file-Part, MissingServletRequestPartException)
        // läuft über sendError() und den ERROR-Dispatch — gleiche Fehlerklasse wie 408/409.
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/import/pdf",
                new HttpEntity<>(new LinkedMultiValueMap<String, Object>(),
                        authenticatedMultipartHeaders()),
                String.class);

        assertThat(response.getStatusCode().value())
                .as("400 muss den ERROR-Dispatch überleben, nicht als 401 ankommen")
                .isEqualTo(400);
    }
}
