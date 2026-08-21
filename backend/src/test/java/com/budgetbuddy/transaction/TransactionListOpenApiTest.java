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
 * Belegt die letzte Acceptance Criteria von #153: «Neuer/geänderter Parameter ist in Swagger UI
 * dokumentiert».
 *
 * <p>Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die Swagger-UI-
 * Seite: die UI rendert genau dieses Dokument, und ein Blick hinein ist kein automatisierter
 * Nachweis (dieselbe Begründung wie in {@code FixedCostOpenApiTest}). Ohne diesen Test wäre die AC
 * nur behauptet — eine vergessene {@code @Parameter}-Annotation fiele niemandem auf.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionListOpenApiTest {

    private static final String LIST_GET = "$.paths['/api/transactions'].get";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "transaction_list_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void paginationParametersAreDocumentedWithTheirDefaults() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LIST_GET + ".parameters[?(@.name == 'page')].description")
                        .isNotEmpty())
                .andExpect(jsonPath(LIST_GET + ".parameters[?(@.name == 'size')].description")
                        .isNotEmpty())
                // Der Standardwert gehört ins Dokument, nicht nur in den Beschreibungstext: er ist
                // die Antwort auf «was liefert ein Aufruf ohne Begrenzung» (AC 3).
                .andExpect(jsonPath(LIST_GET + ".parameters[?(@.name == 'page')].schema.default")
                        .value(0))
                .andExpect(jsonPath(LIST_GET + ".parameters[?(@.name == 'size')].schema.default")
                        .value(20));
    }

    @Test
    void theMonthsEndpointIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transactions/months'].get").exists())
                .andExpect(jsonPath("$.paths['/api/transactions/months'].get.summary").isNotEmpty());
    }

    @Test
    void theResponseSchemaCarriesTheHasMoreFlag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(LIST_GET + ".responses['200'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/TransactionListResponse"))
                .andExpect(jsonPath(
                        "$.components.schemas.TransactionListResponse.properties.hasMore.type")
                        .value("boolean"))
                .andExpect(jsonPath(
                        "$.components.schemas.TransactionListResponse.properties.transactions.type")
                        .value("array"));
    }
}
