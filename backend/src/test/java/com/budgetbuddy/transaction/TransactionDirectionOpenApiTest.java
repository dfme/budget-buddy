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
 * Belegt die Definition of Done von BE-PDF-10: «Neue API-Endpoints sind in Swagger UI sichtbar
 * (OpenAPI-Annotation vorhanden)».
 *
 * <p>Geprüft wird das generierte Dokument unter {@code /v3/api-docs}, nicht die Swagger-UI-Seite —
 * die UI rendert genau dieses Dokument, und ein Blick hinein ist kein automatisierter Nachweis
 * (dieselbe Begründung wie in {@link TransactionListOpenApiTest}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionDirectionOpenApiTest {

    private static final String UNCERTAIN_GET = "$.paths['/api/transactions/uncertain'].get";
    private static final String DIRECTION_PUT = "$.paths['/api/transactions/{id}/direction'].put";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "transaction_direction_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void theUncertainEndpointIsDocumentedIncludingItsMonthParameter() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(UNCERTAIN_GET).exists())
                .andExpect(jsonPath(UNCERTAIN_GET + ".summary").isNotEmpty())
                .andExpect(jsonPath(UNCERTAIN_GET + ".parameters[?(@.name == 'month')].description")
                        .isNotEmpty());
    }

    @Test
    void theDirectionEndpointIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(DIRECTION_PUT).exists())
                .andExpect(jsonPath(DIRECTION_PUT + ".summary").isNotEmpty())
                .andExpect(jsonPath(DIRECTION_PUT + ".responses['404']").exists());
    }

    @Test
    void theTransactionSchemaCarriesTheUncertaintyFlag() throws Exception {
        // Ohne dieses Feld im Schema wüsste ein Client nicht, dass die Richtung einer Buchung eine
        // Annahme sein kann — und die Prüfliste wäre der einzige Ort, an dem er es erführe.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.TransactionResponse.properties.directionUncertain"
                                + ".type")
                        .value("boolean"));
    }
}
