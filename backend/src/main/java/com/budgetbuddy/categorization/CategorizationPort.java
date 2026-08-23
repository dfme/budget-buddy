package com.budgetbuddy.categorization;

import java.util.List;
import java.util.Optional;

/**
 * Port für die Transaktions-Kategorisierung (ADR-6, Hybrid-Ansatz).
 *
 * <p>Kapselt die Kategorisierungsquelle hinter einem Interface, damit sie in Tests mockbar und
 * ohne Refactoring austauschbar ist (Lookup-Tabelle → Claude-API → …). Der erste Schritt der
 * Hybrid-Kategorisierung ist {@link LookupTableService}.
 *
 * <p><strong>{@link #categorizeAll} ist die tragende Methode</strong> (ADR-14, BE-PDF-09): Ein
 * Import kategorisiert 100+ Transaktionen am Stück, und die Laufzeit eines Claude-Calls steckt
 * fast vollständig im Fixkostenanteil pro Request (Netz-Round-Trip, Queueing, Time-to-First-Token)
 * — nicht in der Generierung. Einzeln abgefragt kostete ein 108-Zeilen-Auszug ~41 sequentielle
 * Requests à ~1.1s und lief damit reproduzierbar in das Zeitbudget (#192). Die Bündelung ist
 * deshalb nicht Feintuning, sondern der Unterschied zwischen «Import funktioniert» und
 * «Import funktioniert nicht».
 */
public interface CategorizationPort {

    /**
     * Ordnet einen Transaktionstext einer {@link Category} zu.
     *
     * @param transactionText Freitext der Transaktion (z. B. {@code "DIGITEC GALAXUS AG 044 913
     *     2323"}), typischerweise aus dem PDF-Import.
     * @return die erkannte Kategorie samt liefernder Stufe ({@link CategorizationResult.Source},
     *     BE-PDF-06: Basis für das Lookup-/Claude-Verhältnis im Import-Log), oder
     *     {@link Optional#empty()}, wenn diese Quelle den Text nicht zuordnen kann (der Aufrufer
     *     eskaliert dann an die nächste Stufe bzw. den Fallback {@code Sonstiges}).
     */
    Optional<CategorizationResult> categorize(String transactionText);

    /**
     * Ordnet mehrere Transaktionstexte in einem Zug zu.
     *
     * <p>Der Default arbeitet sie einzeln ab — korrekt für jede Implementierung, deren Kosten
     * ohnehin pro Text anfallen (etwa der DB-Lookup). Quellen mit hohem Fixkostenanteil pro
     * Aufruf überschreiben ihn und fassen die Texte zusammen; siehe
     * {@link ClaudeCategorizationService}.
     *
     * @param transactionTexts Freitexte in der Reihenfolge des Aufrufers.
     * @return Ergebnisse <strong>positionsgleich</strong> zur Eingabe — Index {@code i} der
     *     Rückgabe gehört zu Index {@code i} der Eingabe. Die Liste hat immer dieselbe Länge wie
     *     die Eingabe; einzelne Einträge können {@link Optional#empty()} sein (leerer Text).
     */
    default List<Optional<CategorizationResult>> categorizeAll(List<String> transactionTexts) {
        return transactionTexts.stream().map(this::categorize).toList();
    }
}
