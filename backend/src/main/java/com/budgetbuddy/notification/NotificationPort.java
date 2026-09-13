package com.budgetbuddy.notification;

import java.util.Set;

/**
 * Schreib- und Lese-Port, über den andere Module Benachrichtigungen erzeugen und ihren
 * Gelesen-Zustand abfragen (Fundament für US-08).
 *
 * <p>Gleiche Bauart wie {@code UserIncomePort}/{@code CategorizationPort}: das Interface steht im
 * <em>liefernden</em> Modul, nicht im aufrufenden, und wird von {@link NotificationService}
 * implementiert.
 */
public interface NotificationPort {

    /**
     * Legt eine neue, ungelesene Benachrichtigung für einen User an.
     *
     * @param userId ID des Users, der die Benachrichtigung erhält.
     * @param type Quelle der Benachrichtigung, z. B. {@code "RECURRING_EXPENSE_DETECTED"}. Bleibt
     *     bewusst ein freier String (siehe {@link Notification}) — nicht leer.
     * @param referenceId optionaler, FK-loser Verweis auf die auslösende Zeile des aufrufenden
     *     Moduls; {@code null}, wenn es keine gibt.
     * @param message Anzeigetext für den Nutzer; nicht leer.
     */
    void create(long userId, String type, Long referenceId, String message);

    /**
     * Liefert die {@code referenceId}s aller ungelesenen Benachrichtigungen eines Users zu einem
     * Typ — für Module, die aus dem Gelesen-Zustand einer Notification ein eigenes «Neu»-Flag auf
     * ihrer eigenen Zeile ableiten wollen (BE-REC-02), ohne den Gelesen-Zustand selbst zu
     * duplizieren.
     *
     * @param userId ID des Users, dessen Benachrichtigungen durchsucht werden.
     * @param type Benachrichtigungs-Typ, z. B. {@code "RECURRING_EXPENSE_DETECTED"}.
     * @return die {@code referenceId}s der ungelesenen Benachrichtigungen dieses Typs; leer, wenn
     *     keine existiert.
     */
    Set<Long> unreadReferenceIds(long userId, String type);
}
