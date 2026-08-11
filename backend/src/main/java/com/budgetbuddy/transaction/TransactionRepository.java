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
     * {@link java.math.BigDecimal} vorgenommen — dieselbe Begründung wie in
     * {@code FixedCostRepository}: ADR-9 wird an einer Stelle durchgesetzt statt an zweien.
     */
    List<Transaction> findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
            Long userId, LocalDate von, LocalDate bis);

    /**
     * Lädt alle <em>Gutschriften</em> ({@code is_income = true}) eines Users, deren Buchungsdatum in
     * das Intervall {@code [von, bis]} fällt — beide Grenzen inklusive. Eingabe der
     * Einkommens-Heuristik (BE-STS-02, US-06), die daraus wiederkehrende Zahlungseingänge ableitet.
     *
     * <p>Spiegelbild zu {@link #findByUserIdAndIncomeFalseAndBuchungsdatumBetween}: die Auswertung
     * läuft in Java mit {@link java.math.BigDecimal}, nicht per Aggregat in SQL. Dieselbe Begründung
     * wie dort — ADR-9 wird an einer Stelle durchgesetzt statt an zweien; hier kommt hinzu, dass die
     * Gruppierung über den normalisierten Buchungstext ohnehin nicht in eine Query passt.
     */
    List<Transaction> findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
            Long userId, LocalDate von, LocalDate bis);

    /**
     * Duplikatcheck des PDF-Imports (BE-PDF-02): {@code true}, wenn dieser User bereits
     * Transaktionen aus dem PDF mit diesem SHA-256 importiert hat. Pro User — dasselbe PDF darf
     * von einem anderen User (z. B. Gemeinschaftskonto) erneut importiert werden.
     */
    boolean existsByUserIdAndPdfSha256(Long userId, String pdfSha256);
}
