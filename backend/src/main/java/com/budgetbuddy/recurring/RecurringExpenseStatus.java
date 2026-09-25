package com.budgetbuddy.recurring;

/**
 * Zustand eines Eintrags in {@code recurring_expenses} (Flyway V11, erweitert durch V16).
 *
 * <p>Spiegelt die {@code CHECK (status IN ('DETECTED', 'DISMISSED', 'ENDED'))}-Constraint der
 * Tabelle: Die Wertemenge ist geschlossen, ein vierter Status ist eine Migration <em>und</em> eine
 * Code-Änderung. Gespeichert wird der Name ({@code EnumType.STRING}) — er muss wörtlich mit den
 * Werten der Constraint übereinstimmen, sonst weist Postgres den Insert ab.
 *
 * <p><strong>{@link #ENDED} und {@link #DISMISSED} sind nicht dasselbe</strong> und dürfen es nie
 * werden. {@code DISMISSED} ist die Aussage des <em>Nutzers</em> «das war nie ein Abo» (US-08 AC3)
 * und schliesst den Empfänger dauerhaft aus; {@code ENDED} ist die Beobachtung des <em>Systems</em>
 * «diese Reihe ist in den Daten ausgelaufen» und kehrt sich um, sobald wieder abgebucht wird.
 */
public enum RecurringExpenseStatus {

    /** Laufendes Abo. Erscheint in der Abo-Übersicht und mindert den Safe-to-Spend (FE-FC-05). */
    DETECTED,

    /**
     * Vom Nutzer als «Kein Abo» markiert (BE-REC-02, US-08). Der Empfänger bleibt dauerhaft von
     * der Erkennung ausgeschlossen — deshalb wird die Zeile nicht gelöscht, sondern umgestellt.
     * Terminal: {@link RecurringExpenseService#detect(long)} bewertet einen solchen Eintrag nie
     * neu, weder im Betrag noch im Status.
     */
    DISMISSED,

    /**
     * Ausgelaufene Abo-Reihe (BE-REC-04, V16): Der Empfänger hat in den jüngsten Monaten der
     * Historie nicht mehr abgebucht, das Konto lief aber weiter. Der Eintrag bleibt sichtbar —
     * ein gekündigtes Netflix ist eine Information, keine Fehleingabe —, mindert den
     * Safe-to-Spend aber nicht mehr.
     *
     * <p>Kein Endzustand: bucht der Empfänger in einem späteren Import wieder ab, geht die Zeile
     * beim nächsten Erkennungslauf zurück auf {@link #DETECTED}, ohne neue Benachrichtigung.
     */
    ENDED
}
