package com.budgetbuddy.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * Globale OpenAPI-Metadaten für die Swagger UI.
 */
@OpenAPIDefinition(
    info = @Info(
        title = "BudgetBuddy API",
        version = "v1",
        description = "Safe-to-Spend Budgeting für Studierende und Berufseinsteiger in der Schweiz"
    )
)
@Configuration
public class OpenApiConfig {
}
