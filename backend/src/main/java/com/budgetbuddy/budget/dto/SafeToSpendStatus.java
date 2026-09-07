package com.budgetbuddy.budget.dto;

/**
 * Zustand des Monats, für den ein {@link SafeToSpendResponse} gilt (BE-STS-06, US-12).
 *
 * <p>US-12 verlangt, dass Safe-to-Spend <em>nur für den laufenden Monat berechnet</em> wird und für
 * vergangene Monate stattdessen «Abgeschlossen» erscheint. Der Unterschied ist damit keiner der
 * Zahlen, sondern einer der Bedeutung — und darum ein eigenes Feld statt einer Konvention wie
 * «{@code amount == null} heisst abgeschlossen». Diese Konvention gibt es im selben Objekt bereits
 * für {@code noIncome}, und zwei Bedeutungen an einem {@code null} wären für den Client nicht mehr
 * unterscheidbar.
 *
 * <p>Als Enum und nicht als String: Jackson serialisiert die Konstanten unter ihrem Namen, das
 * Wire-Format ist also {@code "OPEN"} bzw. {@code "CLOSED"}, und Springdoc nimmt die Ausprägungen
 * als {@code enum}-Constraint ins generierte Schema auf. Der Client bekommt die zulässigen Werte
 * damit aus dem Contract statt aus der Dokumentation.
 *
 * <p>Es gibt bewusst <strong>keinen</strong> Wert für Monate in der Zukunft: die beantwortet der
 * Endpoint mit HTTP 400, weil eine Safe-to-Spend-Zahl für einen noch nicht begonnenen Monat nicht
 * definiert ist (Begründung in {@code SafeToSpendService}).
 */
public enum SafeToSpendStatus {

    /** Laufender Monat — der Betrag ist berechnet, alle übrigen Felder tragen ihre übliche Bedeutung. */
    OPEN,

    /**
     * Vergangener Monat — es wurde nicht gerechnet. {@code amount} und {@code incomeSuggestion} sind
     * {@code null}, {@code weeksLeft} ist {@code 0}, {@code negative} und {@code noIncome} sind
     * {@code false}. Der Client zeigt «Abgeschlossen» statt eines Betrags.
     */
    CLOSED
}
