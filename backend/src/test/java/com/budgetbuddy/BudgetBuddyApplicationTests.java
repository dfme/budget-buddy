package com.budgetbuddy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Happy-Path-Skeleton-Test: verifiziert, dass der gesamte Spring-Kontext
 * (Web, Security, JPA/Hibernate-SQLite-Dialekt, Springdoc) fehlerfrei startet.
 */
@SpringBootTest
@ActiveProfiles("test")
class BudgetBuddyApplicationTests {

    @Test
    void contextLoads() {
        // Kontext lädt erfolgreich, wenn keine Exception fliegt.
    }
}
