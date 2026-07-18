package com.budgetbuddy.transaction;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository-Zugriff auf {@link Transaction} (transaction-intern, kein modulübergreifender Zugriff).
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Lädt alle <em>Ausgaben</em> ({@code is_income = false}) eines Users, deren Buchungsdatum in
     * das Intervall {@code [von, bis]} fällt — beide Grenzen inklusive. Für das Kategorie-Summary
     * (BE-CAT-05) werden die Monatsgrenzen im Service berechnet und die Aggregation in Java mit
     * {@link java.math.BigDecimal} vorgenommen (SQLite {@code SUM} über NUMERIC-Affinität liefert
     * Fliesskomma — für rappen-genaue Beträge ungeeignet, ADR-9).
     */
    List<Transaction> findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
            Long userId, LocalDate von, LocalDate bis);
}
