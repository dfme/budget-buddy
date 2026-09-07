package com.budgetbuddy.notification;

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
 * Belegt das AC «Endpoints in Swagger UI sichtbar» von #246 (BE-NOTIF-01).
 *
 * <p>Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die
 * Swagger-UI-Seite selbst — analog {@code FixedCostOpenApiTest}.
 *
 * <p>{@code /v3/api-docs} ist bewusst in {@code SecurityConfig.PUBLIC_PATHS} und wird deshalb ohne
 * JWT abgefragt.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationOpenApiTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "notification_openapi");
    }

    @Autowired private MockMvc mockMvc;

    @Test
    void bothNotificationEndpointsAppearInTheOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/notifications'].get").exists())
                .andExpect(jsonPath("$.paths['/api/notifications/{id}/read'].post").exists());
    }

    @Test
    void endpointsCarryASummarySoTheUiIsReadable() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/notifications'].get.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/notifications/{id}/read'].post.summary")
                        .isNotEmpty());
    }

    @Test
    void responseSchemasAreDeclared() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/notifications'].get.responses['200'].content"
                        + "['*/*'].schema.type").value("array"))
                .andExpect(jsonPath("$.paths['/api/notifications/{id}/read'].post"
                        + ".responses['200'].content['*/*'].schema.$ref")
                        .value("#/components/schemas/NotificationResponse"));
    }
}
