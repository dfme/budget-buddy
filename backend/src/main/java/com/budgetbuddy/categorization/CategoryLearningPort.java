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
 *
 * <p><strong>Das setzt voraus, dass beide Quellen denselben Text übergeben</strong> —
 * Buchungstext samt Detailzeilen, mit Leerzeichen verbunden. Der Primärschlüssel dieser Tabelle
 * ist das Pattern selbst; übergeben die Quellen verschiedene Texte, greift kein Upsert,
 * sondern es entstehen zwei Zeilen, und die Längensortierung in
 * {@link CategoryLookupRepository#findMatching} lässt den längeren Claude-Eintrag über die
 * User-Korrektur gewinnen. Auf beiden Seiten liefert {@code fullText()} den Text
 * ({@code ParsedTransaction} beim Import, {@code Transaction} bei der Korrektur).
 *
 * <p><strong>Gespeichert wird davon das stabile Präfix, nicht der volle Text</strong>
 * (BE-CAT-13): Die Implementierung schneidet die variable Mitteilung — Monat, Jahr, Datum,
 * Referenz — ab, bevor sie schreibt ({@link LookupPatternExtractor}). Aus zwölf Mietzahlungen
 * eines Jahres wird so eine Zeile, die ab dem zweiten Bündel trifft. Für die Aufrufer ändert
 * das nichts: Sie übergeben weiterhin {@code fullText()}, und weil beide Quellen durch denselben
 * Schnitt gehen, bleibt der Upsert intakt.
 */
public interface CategoryLearningPort {

    /**
     * Merkt sich, dass Transaktionstexte, die {@code merchantPattern} enthalten, zu
     * {@code category} gehören. Existiert bereits ein Eintrag für dieses Pattern, wird seine
     * Kategorie überschrieben (Upsert) — der jüngste Aufruf gewinnt.
     *
     * @param merchantPattern der volle Transaktionstext aus Buchungstext und Detailzeilen
     *     ({@code fullText()}) — bei BE-CAT-04 der der korrigierten Transaktion, bei BE-CAT-11
     *     der, den die Claude-Stufe eingestuft hat. Gespeichert wird sein stabiles Präfix, das
     *     (case-insensitiv, als Substring) in künftigen Transaktionstexten gematcht wird.
     * @param category die Zielkategorie — vom User bestätigt (BE-CAT-04) oder von Claude ermittelt
     *     (BE-CAT-11).
     */
    void learn(String merchantPattern, Category category);
}
