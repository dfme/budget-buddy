package com.budgetbuddy.categorization;

/**
 * Schreib-Port der Hybrid-Kategorisierung (ADR-6, Schritt 4 — Lerneffekt): trägt eine Zuordnung
 * {@code Händler-Pattern → Kategorie} in die {@code category_lookup}-Tabelle ein, damit künftige
 * Transaktionen desselben Händlers deterministisch und ohne Claude-Call kategorisiert werden.
 *
 * <p>Gegenstück zum Lese-{@link CategorizationPort}. <strong>Zwei Quellen schreiben hier hinein</strong>
 * — beide in dieselbe Tabelle, mit Upsert-Semantik:
 *
 * <ul>
 *   <li><strong>Manuelle Korrektur</strong> (BE-CAT-04): das {@code transaction}-Modul, wenn ein
 *       User die Kategorie einer Transaktion ändert. Über diesen Port statt direkt aufs
 *       {@link CategoryLookupRepository}, um die Modulgrenze zu wahren (siehe CLAUDE.md).</li>
 *   <li><strong>Erfolgreiche Claude-Kategorisierung</strong> (BE-CAT-11): der
 *       {@link HybridCategorizationService}, sobald die Claude-Stufe einen bis dahin unbekannten
 *       Händler <em>tatsächlich beantwortet</em> hat. Fehler-Fallbacks lernen nicht — die
 *       Begründung steht bei {@code HybridCategorizationService#learnFromClaude}.</li>
 * </ul>
 *
 * <p>Die Reihenfolge entscheidet, und das ist so gewollt: Eine manuelle Korrektur, die nach einer
 * Claude-Einstufung kommt, überschreibt sie. Der User hat in dieser Tabelle das letzte Wort.
 */
public interface CategoryLearningPort {

    /**
     * Merkt sich, dass Transaktionstexte, die {@code merchantPattern} enthalten, zu
     * {@code category} gehören. Existiert bereits ein Eintrag für dieses Pattern, wird seine
     * Kategorie überschrieben (Upsert) — der jüngste Aufruf gewinnt.
     *
     * @param merchantPattern Händler-Pattern, das (case-insensitiv, als Substring) im
     *     Transaktionstext gematcht wird. Bei BE-CAT-04 der {@code buchungstext} der korrigierten
     *     Transaktion, bei BE-CAT-11 der rohe Transaktionstext, den die Claude-Stufe eingestuft
     *     hat.
     * @param category die Zielkategorie — vom User bestätigt (BE-CAT-04) oder von Claude ermittelt
     *     (BE-CAT-11).
     */
    void learn(String merchantPattern, Category category);
}
