package com.budgetbuddy.budget;

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
 * Belegt AC 3 von #12: «Alle Endpoints in Swagger UI sichtbar mit Request/Response-Schema».
 *
 * <p>Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die Swagger-UI-
 * Seite selbst: die UI rendert genau dieses Dokument, und ein Blick ins UI ist kein automatisierter
 * Nachweis. Fällt eine {@code @Operation}-Annotation weg oder verschwindet ein Endpoint aus dem
 * Mapping, wird dieser Test rot — ein manueller Klick durch die UI würde es niemandem melden.
 *
 * <p>{@code /v3/api-docs} ist bewusst in {@code SecurityConfig.PUBLIC_PATHS} und wird deshalb ohne
 * JWT abgefragt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FixedCostOpenApiTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "fixed_cost_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void allFixedCostEndpointsAppearInTheOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/fixed-costs'].get").exists())
                .andExpect(jsonPath("$.paths['/api/fixed-costs'].post").exists())
                .andExpect(jsonPath("$.paths['/api/fixed-costs/{id}'].get").exists())
                .andExpect(jsonPath("$.paths['/api/fixed-costs/{id}'].put").exists())
                .andExpect(jsonPath("$.paths['/api/fixed-costs/{id}'].delete").exists())
                .andExpect(jsonPath("$.paths['/api/users/me/onboarding-complete'].post").exists());
    }

    @Test
    void endpointsCarryASummarySoTheUiIsReadable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/fixed-costs'].post.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/fixed-costs/{id}'].delete.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/users/me/onboarding-complete'].post.summary")
                        .isNotEmpty());
    }

    /**
     * Die Media-Types sind bewusst asymmetrisch abgefragt, weil Springdoc sie asymmetrisch erzeugt:
     * der Request-Body bekommt {@code application/json} (aus {@code @RequestBody} plus Jackson), die
     * Responses den Platzhalter {@code *}{@code /*}, weil die Controller dieses Projekts kein
     * {@code produces} deklarieren. Beim bestehenden {@code PUT /transactions/{id}/category} steht
     * derselbe Platzhalter. Der Test hält damit das tatsächliche Verhalten fest, nicht das
     * erwartete — die erste Fassung dieser Assertion nahm zweimal {@code application/json} an und
     * war beide Male falsch.
     */
    @Test
    void requestAndResponseSchemasAreDeclared() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // Request-Schema von POST /fixed-costs
                .andExpect(jsonPath("$.paths['/api/fixed-costs'].post.requestBody.content"
                        + "['application/json'].schema.$ref")
                        .value("#/components/schemas/FixedCostRequest"))
                // Response-Schema von POST /fixed-costs (201)
                .andExpect(jsonPath("$.paths['/api/fixed-costs'].post.responses['201'].content"
                        + "['*/*'].schema.$ref").value("#/components/schemas/FixedCostResponse"))
                // Response-Schema der Übersicht
                .andExpect(jsonPath("$.paths['/api/fixed-costs'].get.responses['200'].content"
                        + "['*/*'].schema.$ref")
                        .value("#/components/schemas/FixedCostSummaryResponse"))
                // Fehler-Schema mit Feldname (US-03). Springdoc leitet es aus dem
                // FixedCostExceptionHandler ab — der Body ist damit auch dokumentiert, nicht nur
                // implementiert.
                .andExpect(jsonPath("$.paths['/api/fixed-costs'].post.responses['400'].content"
                        + "['*/*'].schema.$ref")
                        .value("#/components/schemas/FixedCostErrorResponse"));
    }

    @Test
    void schemaComponentsDescribeTheWireFormat() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // intervall ist im Schema ein String (ASCII-Label), kein Enum-Konstantenname —
                // der Contract-Punkt aus dem Kommentar an #12.
                .andExpect(jsonPath("$.components.schemas.FixedCostRequest.properties.intervall.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.FixedCostResponse.properties.intervall.type")
                        .value("string"))
                // betrag ist eine Zahl, kein String.
                .andExpect(jsonPath("$.components.schemas.FixedCostRequest.properties.betrag.type")
                        .value("number"))
                .andExpect(jsonPath("$.components.schemas.FixedCostResponse.properties.monatsbetrag.type")
                        .value("number"))
                .andExpect(jsonPath("$.components.schemas.FixedCostErrorResponse.properties.field")
                        .exists());
    }
}
