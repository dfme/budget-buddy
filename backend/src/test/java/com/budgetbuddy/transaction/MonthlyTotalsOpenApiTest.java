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
 * Belegt die Definition-of-Done von #288: «Neuer Endpoint ist in Swagger UI sichtbar
 * (OpenAPI-Annotation vorhanden)».
 *
 * <p>Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die
 * Swagger-UI-Seite: die UI rendert genau dieses Dokument, und ein Blick hinein ist kein
 * automatisierter Nachweis (dieselbe Begründung wie in {@link TransactionListOpenApiTest}). Ohne
 * diesen Test wäre die AC nur behauptet — eine vergessene {@code @Parameter}-Annotation fiele
 * niemandem auf.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MonthlyTotalsOpenApiTest {

    private static final String TOTALS_GET = "$.paths['/api/transactions/monthly-totals'].get";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "monthly_totals_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void theEndpointIsDocumentedUnderTheTransactionsTag() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TOTALS_GET).exists())
                .andExpect(jsonPath(TOTALS_GET + ".summary").isNotEmpty())
                .andExpect(jsonPath(TOTALS_GET + ".description").isNotEmpty())
                // Derselbe Tag wie die übrigen Transaktions-Endpoints, damit der Endpoint in der
                // UI nicht in einer eigenen Gruppe landet.
                .andExpect(jsonPath(TOTALS_GET + ".tags[0]").value("Transactions"));
    }

    @Test
    void bothParametersAreDocumentedAndMonthsCarriesItsDefault() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TOTALS_GET + ".parameters[?(@.name == 'month')].description")
                        .isNotEmpty())
                .andExpect(jsonPath(TOTALS_GET + ".parameters[?(@.name == 'months')].description")
                        .isNotEmpty())
                // Der Standardwert gehört ins Dokument, nicht nur in den Beschreibungstext: er ist
                // die Antwort auf «wie gross ist das Fenster ohne Angabe» (dieselbe Überlegung wie
                // bei page/size in TransactionListOpenApiTest).
                .andExpect(jsonPath(TOTALS_GET + ".parameters[?(@.name == 'months')].schema.default")
                        .value(MonthlyTotalsService.DEFAULT_WINDOW_MONTHS))
                // month ist Pflicht wie bei GET /api/transactions/summary; months nicht.
                .andExpect(jsonPath(TOTALS_GET + ".parameters[?(@.name == 'month')].required")
                        .value(true));
    }

    @Test
    void theBadRequestResponseIsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(TOTALS_GET + ".responses.['400'].description").isNotEmpty())
                .andExpect(jsonPath(TOTALS_GET + ".responses.['401'].description").isNotEmpty());
    }
}
