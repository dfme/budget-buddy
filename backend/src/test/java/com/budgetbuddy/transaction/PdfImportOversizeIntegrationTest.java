package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.auth.JwtService;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Endpoint-Test des 413-Pfads von {@code POST /import/pdf} (BE-PDF-03, AC "File-Size-Limit 10 MB
 * wird serverseitig durchgesetzt").
 *
 * <p>Bewusst gegen einen echten Port ({@link TestRestTemplate}) statt MockMvc: MockMvc umgeht die
 * reale Multipart-Auflösung und würde das Grössenlimit gar nicht durchsetzen. Hier läuft der
 * vollständige Stack (Tomcat + Spring-Multipart-Resolver), sodass das Limit tatsächlich greift.
 * Das Limit wird für den Test auf 1&nbsp;KB gesenkt; das echte 5-KB-Fixture überschreitet es und
 * beweist die serverseitige Durchsetzung, ohne 10&nbsp;MB zu allozieren.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportOversizeIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-pdf-03-oversize-it", ".db");
            Files.deleteIfExists(file);
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE);
        registry.add("spring.flyway.enabled", () -> "true");
        // Limit für den Test auf 1 KB gesenkt; das 5-KB-Fixture überschreitet es.
        registry.add("spring.servlet.multipart.max-file-size", () -> "1KB");
        registry.add("spring.servlet.multipart.max-request-size", () -> "1KB");
    }

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JwtService jwtService;

    private static byte[] fixture() {
        try (InputStream in = PdfImportOversizeIntegrationTest.class
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
    void uploadExceedingSizeLimitReturns413() {
        ByteArrayResource filePart = new ByteArrayResource(fixture()) {
            @Override
            public String getFilename() {
                return "kontoauszug.pdf";
            }
        };
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, "jwt=" + jwtService.generateToken(1L));

        ResponseEntity<String> response = restTemplate.postForEntity(
                "/import/pdf", new HttpEntity<>(body, headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
