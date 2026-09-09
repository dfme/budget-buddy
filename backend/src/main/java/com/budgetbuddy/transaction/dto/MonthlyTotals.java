package com.budgetbuddy.transaction.dto;

import java.math.BigDecimal;

/**
 * Die Kennzahlen eines Monats für {@code GET /api/transactions/monthly-totals} (BE-STS-07, US-12).
 *
 * <p>Eine Zeile der Drei-Monats-Übersicht des Dashboards (FE-STS-04). Die Antwort des Endpoints ist
 * eine Liste dieser Records, neuester Monat zuerst, mit einer Zeile für <em>jeden</em> Monat des
 * abgefragten Fensters — auch für einen ohne Buchungen. Der Client soll drei Zeilen rendern können
 * und keine Lücken füllen müssen.
 *
 * <p>Kein Envelope-Record um die Liste: es gibt keine Kennzahl über dem Fenster, die einer trüge.
 * {@code GET /api/transactions/months} liefert aus demselben Grund ein nacktes Array.
 *
 * <p>Die Feldnamen sind zugleich das Wire-Format — Jackson serialisiert Record-Komponenten unter
 * ihrem Namen.
 *
 * @param month der Monat im Format {@code YYYY-MM}, z. B. {@code "2026-07"}.
 * @param income Summe der Gutschriften ({@code is_income = true}) des Monats in CHF
 *     ({@link BigDecimal}, Skala 2, ADR-9). {@code null} genau dann, wenn der Monat
 *     <em>keine einzige</em> Buchung trägt — siehe {@link #difference}.
 * @param expenses Summe der Belastungen ({@code is_income = false}) des Monats in CHF, Skala 2.
 *     Betragsgleich mit {@link CategorySummaryResponse#totalAmount()} desselben Monats: dieselbe
 *     Auswahlregel, dieselbe Query — nur Belastungen, ganzer Monat, <strong>kein</strong> Abzug der
 *     per Dauerauftrag bezahlten Fixkosten. Der Fixkosten-Abzug aus ADR-13 gehört allein in den
 *     Safe-to-Spend-Summanden; diese Übersicht zeigt wie die Kategorie-Übersicht die echten
 *     Belastungen des Kontos. {@code null} unter derselben Bedingung wie {@link #income}.
 * @param difference {@code income − expenses} in CHF, Skala 2 — <strong>serverseitig</strong> als
 *     {@link BigDecimal#subtract} gerechnet und mitgeliefert, nicht dem Client überlassen. Dort
 *     wäre es die Subtraktion zweier JSON-{@code number}, also genau die Gleitkomma-Rechnung, die
 *     ADR-9 für Geldbeträge ausschliesst. Negativ, wenn im Monat mehr ausgegeben als eingenommen
 *     wurde.
 *
 *     <p><strong>Warum {@code null} und nicht {@code 0.00}.</strong> Alle drei Beträge sind
 *     {@code null}, wenn der Monat keine Buchung trägt. {@code 0.00} behauptete erfasste
 *     Nullbeträge; {@code null} bleibt davon unterscheidbar — dieselbe Unterscheidung, die
 *     {@code SafeToSpendResponse.amount} bereits trägt, und die Grundlage für die
 *     {@code –}-Darstellung in FE-STS-04. Ein Monat mit ausschliesslich Gutschriften trägt
 *     folglich {@code expenses = 0.00} und nicht {@code null}: dort <em>gibt</em> es Buchungen, und
 *     die Summe der Belastungen ist dann wirklich null.
 */
public record MonthlyTotals(
        String month, BigDecimal income, BigDecimal expenses, BigDecimal difference) {}
