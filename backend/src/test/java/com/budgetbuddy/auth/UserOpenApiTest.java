package com.budgetbuddy.auth;

import static org.hamcrest.Matchers.containsString;
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
 * Belegt die AC «OpenAPI-Annotation vorhanden, Endpoint in der Swagger UI sichtbar» von
 * BE-AUTH-09 (#176) und BE-AUTH-14 (#290).
 *
 * <p>Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die Swagger-UI-
 * Seite: die UI rendert genau dieses Dokument, und ein Blick ins UI ist kein automatisierter
 * Nachweis — dieselbe Begründung wie in {@code FixedCostOpenApiTest}. Fällt die
 * {@code @Operation}-Annotation weg oder verschwindet der Endpoint aus dem Mapping, wird dieser
 * Test rot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserOpenApiTest {

    private static final String PASSWORD_PUT = "$.paths['/api/users/me/password'].put";
    private static final String ME_DELETE = "$.paths['/api/users/me'].delete";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "user_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void changePasswordEndpointIsDocumentedWithASummary() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PASSWORD_PUT).exists())
                .andExpect(jsonPath(PASSWORD_PUT + ".summary").isNotEmpty());
    }

    @Test
    void requestAndErrorSchemasAreDeclared() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(PASSWORD_PUT + ".requestBody.content['application/json']"
                        + ".schema.$ref")
                        .value("#/components/schemas/ChangePasswordRequest"))
                // Springdoc leitet das 400-Schema aus dem UserExceptionHandler ab — der Body ist
                // damit auch dokumentiert, nicht nur implementiert (analog FixedCostOpenApiTest).
                .andExpect(jsonPath(PASSWORD_PUT + ".responses['400'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/AuthErrorResponse"))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest"
                        + ".properties.aktuellesPasswort.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ChangePasswordRequest"
                        + ".properties.neuesPasswort.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.AuthErrorResponse.properties.message"
                        + ".type").value("string"));
    }

    // --- DELETE /users/me (BE-AUTH-14) ---

    @Test
    void deleteAccountEndpointIsDocumentedAsIrreversibleAndNamesTheKnownGaps() throws Exception {
        // AC: «dokumentiert, dass die Löschung endgültig ist» und «benennt die zwei offenen
        // Lücken» — beides steht in der description, damit es in der Swagger UI sichtbar ist und
        // nicht nur in einem Javadoc, das die UI nie erreicht.
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ME_DELETE).exists())
                .andExpect(jsonPath(ME_DELETE + ".summary").isNotEmpty())
                .andExpect(jsonPath(ME_DELETE + ".description").value(containsString("endgültig")))
                .andExpect(jsonPath(ME_DELETE + ".description")
                        .value(containsString("category_lookup")))
                .andExpect(jsonPath(ME_DELETE + ".description")
                        .value(containsString("recurring_expenses")));
    }

    @Test
    void deleteAccountRequestAndErrorSchemasAreDeclared() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(ME_DELETE + ".requestBody.content['application/json']"
                        + ".schema.$ref")
                        .value("#/components/schemas/DeleteAccountRequest"))
                .andExpect(jsonPath(ME_DELETE + ".responses['204']").exists())
                .andExpect(jsonPath(ME_DELETE + ".responses['400'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/AuthErrorResponse"))
                .andExpect(jsonPath(ME_DELETE + ".responses['401']").exists())
                .andExpect(jsonPath("$.components.schemas.DeleteAccountRequest"
                        + ".properties.passwort.type").value("string"));
    }
}
