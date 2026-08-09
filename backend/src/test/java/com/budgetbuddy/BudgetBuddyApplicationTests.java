package com.budgetbuddy;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Happy-Path-Skeleton-Test: verifiziert, dass der gesamte Spring-Kontext
 * (Web, Security, JPA/Hibernate, Springdoc) fehlerfrei startet.
 */
@SpringBootTest
@ActiveProfiles("test")
class BudgetBuddyApplicationTests {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.registerWithoutFlyway(registry, "app_context");
    }

    @Test
    void contextLoads() {
        // Kontext lädt erfolgreich, wenn keine Exception fliegt.
    }
}
