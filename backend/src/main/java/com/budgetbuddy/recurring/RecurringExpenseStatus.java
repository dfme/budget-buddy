package com.budgetbuddy.recurring;

/**
 * Zustand eines Eintrags in {@code recurring_expenses} (Flyway V11, DB-09).
 *
 * <p>Spiegelt die {@code CHECK (status IN ('DETECTED', 'DISMISSED'))}-Constraint der Tabelle:
 * Die Wertemenge ist geschlossen, ein dritter Status ist eine Migration <em>und</em> eine
 * Code-Änderung. Gespeichert wird der Name ({@code EnumType.STRING}) — er muss wörtlich mit den
 * Werten der Constraint übereinstimmen, sonst weist Postgres den Insert ab.
 */
public enum RecurringExpenseStatus {

    /** Von der Erkennung (BE-REC-01) geschrieben; erscheint in der Abo-Übersicht. */
    DETECTED,

    /**
     * Vom Nutzer als «Kein Abo» markiert (BE-REC-02, US-08). Der Empfänger bleibt dauerhaft von
     * der Erkennung ausgeschlossen — deshalb wird die Zeile nicht gelöscht, sondern umgestellt.
     */
    DISMISSED
}
