package com.budgetbuddy.auth;

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
 * Belegt die AC «Swagger UI zeigt die erweiterten DTOs» von BE-AUTH-05 (#114).
 *
 * <p>Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die Swagger-UI-
 * Seite: die UI rendert genau dieses Dokument (analog {@code FixedCostOpenApiTest},
 * {@code UserOpenApiTest}). {@code firstName}/{@code lastName} brauchen keine eigene
 * {@code @Operation}-Annotation — Springdoc leitet das Schema direkt aus den Record-Feldern von
 * {@link com.budgetbuddy.auth.dto.RegisterRequest} und
 * {@link com.budgetbuddy.auth.dto.UserProfileResponse} ab, die der bereits dokumentierte
 * {@code /api/auth/register}-Endpoint referenziert.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthOpenApiTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "auth_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void registerRequestSchemaIncludesFirstNameAndLastName() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.firstName.type")
                        .value("string"))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.lastName.type")
                        .value("string"));
    }

    /**
     * Belegt die DoD-Angabe «maxLength erscheint im Schema» von BE-AUTH-12 (#231): Springdoc
     * leitet {@code maxLength} direkt aus den {@code @Size(max = …)}-Annotationen auf
     * {@link com.budgetbuddy.auth.dto.RegisterRequest} ab, keine eigene {@code @Schema}-Angabe
     * nötig.
     */
    @Test
    void registerRequestSchemaIncludesMaxLengthOfEmailFirstNameAndLastName() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.email.maxLength")
                        .value(254))
                .andExpect(
                        jsonPath("$.components.schemas.RegisterRequest.properties.firstName.maxLength")
                                .value(50))
                .andExpect(
                        jsonPath("$.components.schemas.RegisterRequest.properties.lastName.maxLength")
                                .value(50));
    }

    @Test
    void userProfileResponseSchemaIncludesFirstNameAndLastName() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.components.schemas.UserProfileResponse.properties.firstName.type")
                        .value("string"))
                .andExpect(jsonPath(
                        "$.components.schemas.UserProfileResponse.properties.lastName.type")
                        .value("string"));
    }
}
