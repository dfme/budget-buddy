package com.budgetbuddy.notification;

/**
 * Schreib-Port, über den andere Module Benachrichtigungen erzeugen (Fundament für US-08).
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
}
