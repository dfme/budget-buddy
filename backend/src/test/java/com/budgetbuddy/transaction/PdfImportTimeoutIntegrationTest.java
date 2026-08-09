package com.budgetbuddy.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.categorization.CategorizationPort;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoint-Test des 408-Pfads von {@code POST /import/pdf} (BE-PDF-03): Bei überschrittenem
 * Zeitbudget muss der Upload mit {@code 408 Request Timeout} antworten.
 *
 * <p>Eigener Context, weil {@code budgetbuddy.import.timeout-seconds} in den
 * {@link PdfImportService} konstruktor-injiziert wird und daher pro Context feststeht. Wert 0 →
 * die Deadline liegt beim Start; der {@link PdfImportService} bricht direkt nach dem Parsen (vor
 * der Kategorisierung) mit {@link PdfImportTimeoutException} ab, das {@code @ResponseStatus(408)}
 * dieser Exception liefert den Status.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportTimeoutIntegrationTest {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "pdf_import_timeout");
        // Zeitbudget 0 → jeder Import läuft sofort in den Timeout.
        registry.add("budgetbuddy.import.timeout-seconds", () -> "0");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;

    /** Kette gemockt; im Timeout-Fall wird sie ohnehin nie erreicht (Abbruch nach dem Parsen). */
    @MockitoBean(name = "hybridCategorizationService")
    private CategorizationPort categorizationPort;

    private static byte[] fixture() {
        try (InputStream in = PdfImportTimeoutIntegrationTest.class
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
    void importExceedingTimeBudgetReturns408() throws Exception {
        MockMultipartFile file =
                new MockMultipartFile("file", "kontoauszug.pdf", "application/pdf", fixture());
        Cookie jwt = new Cookie("jwt", jwtService.generateToken(1L));

        mockMvc.perform(multipart("/import/pdf").file(file).cookie(jwt))
                .andExpect(status().isRequestTimeout());
    }
}
