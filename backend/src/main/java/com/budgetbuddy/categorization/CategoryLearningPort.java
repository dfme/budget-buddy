package com.budgetbuddy.categorization;

/**
 * Schreib-Port der Hybrid-Kategorisierung (ADR-6, Schritt 3 — Lerneffekt): trägt eine vom User
 * bestätigte Zuordnung {@code Händler-Pattern → Kategorie} in die {@code category_lookup}-Tabelle
 * ein, damit künftige Transaktionen desselben Händlers deterministisch und ohne Claude-Call
 * kategorisiert werden.
 *
 * <p>Gegenstück zum Lese-{@link CategorizationPort}. Über dieses Interface schreibt das
 * {@code transaction}-Modul (BE-CAT-04, manuelle Korrektur), ohne direkt auf das
 * {@link CategoryLookupRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md).
 */
public interface CategoryLearningPort {

    /**
     * Merkt sich, dass Transaktionstexte, die {@code merchantPattern} enthalten, zu
     * {@code category} gehören. Existiert bereits ein Eintrag für dieses Pattern, wird seine
     * Kategorie überschrieben (Upsert) — eine erneute Korrektur gewinnt.
     *
     * @param merchantPattern Händler-Pattern, das (case-insensitiv, als Substring) im
     *     Transaktionstext gematcht wird. Bei BE-CAT-04 der {@code buchungstext} der korrigierten
     *     Transaktion.
     * @param category die vom User bestätigte Zielkategorie.
     */
    void learn(String merchantPattern, Category category);
}
