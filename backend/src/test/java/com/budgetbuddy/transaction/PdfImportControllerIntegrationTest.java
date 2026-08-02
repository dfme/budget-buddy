package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.Category;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest von {@code POST /import/pdf} (BE-PDF-03) gegen echtes SQLite + Flyway und das
 * echte UBS-Fixture. Deckt die Acceptance Criteria am Endpoint ab: 200 mit Anzahl, 409 Duplikat,
 * 400 ungültiges PDF, 408 Timeout, 401 ohne Auth.
 *
 * <p>Die Kategorisierung ist per {@link MockitoBean} auf dem {@code hybridCategorizationService}
 * (dem {@code @Primary}-Port) gemockt — kein Claude-Call im Test (analog
 * {@link PdfImportServiceIntegrationTest}). Der 413-Fall (Oversize) braucht ein eigenes
 * Multipart-Limit und liegt daher in {@link PdfImportOversizeIntegrationTest}.
 *
 * <p>Temp-File-DB statt {@code jdbc:sqlite::memory:} und {@code @DirtiesContext} analog zu den
 * übrigen transaction-Integrationstests.
 *
 * <p><b>Grenze von MockMvc:</b> Der ERROR-Dispatch auf {@code /error} (via {@code
 * @ResponseStatus} → {@code sendError()}) wird hier nicht ausgeführt — 408/409 waren unter
 * MockMvc grün, kamen real aber als 401 an. Diese Seite deckt
 * {@link PdfImportErrorDispatchIntegrationTest} über einen echten Container ab.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportControllerIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-pdf-03-import-it", ".db");
            Files.deleteIfExists(file); // Flyway/SQLite legt die Datei selbst an
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionRepository transactionRepository;

    /** Ersetzt die Hybrid-Kette (den {@code @Primary}-{@link CategorizationPort}) im Kontext. */
    @MockitoBean(name = "hybridCategorizationService")
    private CategorizationPort categorizationPort;

    private long userId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
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

    private Cookie jwtCookie(long uid) {
        return new Cookie("jwt", jwtService.generateToken(uid));
    }

    private static MockMultipartFile pdfPart(byte[] content) {
        return new MockMultipartFile("file", "kontoauszug.pdf", "application/pdf", content);
    }

    private static byte[] fixture() {
        try (InputStream in = PdfImportControllerIntegrationTest.class
                .getResourceAsStream("/pdf/UBS_Konto_Bewegungen_2021_Juli.pdf")) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Erzeugt ein unverschlüsseltes Text-PDF mit den gegebenen Zeilen (PDFBox 3.x). */
    private static byte[] pdfWithLines(List<String> lines) {
        try (PDDocument document = new PDDocument()) {
            writeLines(document, lines);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Erzeugt ein AES-128-verschlüsseltes PDF (PDFBox 3.x) — analog SwissBankStatementParserTest. */
    private static byte[] passwordProtectedPdf() {
        try (PDDocument document = new PDDocument()) {
            writeLines(document, List.of("Kontoauszug", "03.07.2026 MIGROS BERN 45.60 954.40"));
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-pw", "user-pw", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeLines(PDDocument document, List<String> lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(FontName.HELVETICA), 10);
            content.newLineAtOffset(50, 780);
            for (String line : lines) {
                content.showText(line);
                content.newLineAtOffset(0, -14);
            }
            content.endText();
        }
    }

    @Test
    void validPdfReturns200WithTransactionCount() throws Exception {
        mockMvc.perform(multipart("/import/pdf").file(pdfPart(fixture())).cookie(jwtCookie(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(28));

        // Kreuzprobe: die 28 Transaktionen sind auch wirklich persistiert.
        assertThat(transactionRepository.count()).isEqualTo(28);
    }

    @Test
    void recognizedStatementWithoutBookingsReturns200WithCountZero() throws Exception {
        // BE-PDF-05 (#95): Format erkannt (Saldovortrag-Zeile), aber keine Buchung — Konto ohne
        // Bewegung. Erfolg mit count=0 statt 400 "Format nicht unterstützt" (Team-Entscheid).
        byte[] emptyStatement = pdfWithLines(List.of(
                "Kontoauszug Maerz 2024",
                "Saldovortrag 1'000.00",
                "Schlusssaldo 1'000.00"));

        mockMvc.perform(multipart("/import/pdf")
                        .file(pdfPart(emptyStatement)).cookie(jwtCookie(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        assertThat(transactionRepository.count()).isZero();
    }

    @Test
    void duplicatePdfReturns409() throws Exception {
        mockMvc.perform(multipart("/import/pdf").file(pdfPart(fixture())).cookie(jwtCookie(userId)))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/import/pdf").file(pdfPart(fixture())).cookie(jwtCookie(userId)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidPdfReturns400WithUnsupportedFormatReason() throws Exception {
        byte[] notAPdf = "Dies ist kein PDF".getBytes(StandardCharsets.UTF_8);

        // Der reason macht die beiden 400er für das Frontend unterscheidbar (FE-PDF-02).
        mockMvc.perform(multipart("/import/pdf").file(pdfPart(notAPdf)).cookie(jwtCookie(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("UNSUPPORTED_FORMAT"));
    }

    @Test
    void passwordProtectedPdfReturns400WithPasswordProtectedReason() throws Exception {
        // Führt den PasswordProtectedPdfException-Zweig des PdfImportExceptionHandler am Endpoint
        // aus. Verschlüsselung wird vor dem Parsen erkannt, der Inhalt ist daher belanglos.
        mockMvc.perform(multipart("/import/pdf")
                        .file(pdfPart(passwordProtectedPdf())).cookie(jwtCookie(userId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reason").value("PASSWORD_PROTECTED"));
    }

    @Test
    void withoutJwtReturns401() throws Exception {
        mockMvc.perform(multipart("/import/pdf").file(pdfPart(fixture())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingFilePartReturns400() throws Exception {
        mockMvc.perform(multipart("/import/pdf").cookie(jwtCookie(userId)))
                .andExpect(status().isBadRequest());
    }
}
