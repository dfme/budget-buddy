package com.budgetbuddy.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Belegt die Definition of Done von BE-PDF-09: «Neue API-Endpoints sind in Swagger UI sichtbar».
 *
 * <p>Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die
 * Swagger-UI-Seite: die UI rendert genau dieses Dokument, und ein Blick hinein ist kein
 * automatisierter Nachweis (dieselbe Begründung wie in {@link TransactionListOpenApiTest}).
 *
 * <p>Der Statuswechsel des Uploads von 200 auf 202 gehört mit ins Dokument: Ein Client, der nach
 * ADR-14 weiterhin 200 erwartet, wartet auf ein Ergebnis, das nie kommt — der dokumentierte
 * Status ist hier ein Vertragsdetail, kein Schmuck.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfImportOpenApiTest {

    private static final String UPLOAD_POST = "$.paths['/api/import/pdf'].post";
    private static final String STATUS_GET = "$.paths['/api/import/{jobId}/status'].get";
    private static final String TRANSACTIONS_GET =
            "$.paths['/api/import/{jobId}/transactions'].get";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "pdf_import_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void theStatusEndpointIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(STATUS_GET).exists())
                .andExpect(jsonPath(STATUS_GET + ".summary").isNotEmpty())
                .andExpect(jsonPath(STATUS_GET + ".parameters[?(@.name == 'jobId')].description")
                        .isNotEmpty())
                .andExpect(jsonPath(STATUS_GET + ".responses['404']").exists());
    }

    /**
     * AC 5 von BE-PDF-14: «Endpoint ist in Swagger UI sichtbar». Mitgeprüft werden die beiden
     * Fehlerfälle, die den Vertrag ausmachen — 404 für einen fremden oder unbekannten Job und 409
     * für einen, der noch nicht abgeschlossen ist.
     */
    @Test
    void theImportTransactionsEndpointIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TRANSACTIONS_GET).exists())
                .andExpect(jsonPath(TRANSACTIONS_GET + ".summary").isNotEmpty())
                .andExpect(jsonPath(TRANSACTIONS_GET + ".description").isNotEmpty())
                .andExpect(jsonPath(TRANSACTIONS_GET
                        + ".parameters[?(@.name == 'jobId')].description").isNotEmpty())
                .andExpect(jsonPath(TRANSACTIONS_GET + ".responses['200']").exists())
                .andExpect(jsonPath(TRANSACTIONS_GET + ".responses['404']").exists())
                .andExpect(jsonPath(TRANSACTIONS_GET + ".responses['409']").exists());
    }

    /**
     * Der 409 trägt seinen Stand als Body, nicht nur als Status — genau darin unterscheidet er
     * sich vom Duplikat-409 des Uploads. Ein Client, der zwischen «später nochmal» (RUNNING) und
     * «wird nie etwas liefern» (FAILED) unterscheiden soll, braucht das Feld dokumentiert.
     */
    @Test
    void theConflictBodyDocumentsTheJobStatus() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TRANSACTIONS_GET
                        + ".responses['409'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/ImportNotCompleteResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.ImportNotCompleteResponse.properties.status.enum")
                        .isArray());
    }

    @Test
    void theStatusResponseSchemaCarriesProgressAndDegradedFlag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.ImportJobStatusResponse.properties.processed.type")
                        .value("integer"))
                .andExpect(jsonPath(
                        "$.components.schemas.ImportJobStatusResponse.properties.total.type")
                        .value("integer"))
                .andExpect(jsonPath(
                        "$.components.schemas.ImportJobStatusResponse.properties.degraded.type")
                        .value("boolean"));
    }

    @Test
    void theUploadIsDocumentedAsAcceptedWithAJobId() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // 202, nicht 200: Der Upload startet den Import, er schliesst ihn nicht ab.
                .andExpect(jsonPath(UPLOAD_POST + ".responses['202']").exists())
                .andExpect(jsonPath(UPLOAD_POST + ".responses['200']").doesNotExist())
                .andExpect(jsonPath(
                        "$.components.schemas.ImportStartedResponse.properties.jobId.type")
                        .value("integer"));
    }
}
