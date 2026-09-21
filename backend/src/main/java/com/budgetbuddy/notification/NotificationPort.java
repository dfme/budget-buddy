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
     * @return die ID der neu angelegten Benachrichtigung — für Module, die ihre eigenen Zeilen an
     *     diese Benachrichtigung hängen wollen (FE-NOTIF-04: {@code recurring_expenses.notification_id}).
     */
    long create(long userId, String type, Long referenceId, String message);

    /**
     * Liefert die IDs aller ungelesenen Benachrichtigungen eines Users zu einem Typ — für Module,
     * die aus dem Gelesen-Zustand einer Notification ein eigenes «Neu»-Flag auf ihren eigenen
     * Zeilen ableiten (BE-REC-02), ohne den Gelesen-Zustand selbst zu duplizieren.
     *
     * <p>Seit FE-NOTIF-04 (#336) sind es die IDs der Notifications selbst, nicht mehr ihre
     * {@code referenceId}s: eine Benachrichtigung bündelt seither mehrere Zeilen des aufrufenden
     * Moduls, und der Verweis liegt deshalb auf dessen Seite ({@code notification_id}, V14).
     *
     * @param userId ID des Users, dessen Benachrichtigungen durchsucht werden.
     * @param type Benachrichtigungs-Typ, z. B. {@code "RECURRING_EXPENSE_DETECTED"}.
     * @return die IDs der ungelesenen Benachrichtigungen dieses Typs; leer, wenn keine existiert.
     */
    Set<Long> unreadIds(long userId, String type);

    /**
     * Markiert eine Benachrichtigung eines Users als gelesen — für Module, die eine Bündel-
     * Benachrichtigung abschliessen, weil keine der gebündelten Zeilen mehr offen ist (BE-REC-03,
     * {@code dismiss}): eine Glocke, die weiter für «erkannte Abos» wirbt, die alle verneint
     * wurden, führt ins Leere.
     *
     * <p>Ein Port-Pfad statt eines Rückgriffs auf {@code NotificationService#markAsRead(long, long)}:
     * Letzterer wirft bei unbekannter ID, weil er eine Antwort an einen Request-Client ist. Hier
     * gilt das Gegenteil — ohne passende Benachrichtigung ein No-op, wirft nicht. Idempotent:
     * eine bereits gelesene Benachrichtigung behält ihren ursprünglichen Lesezeitpunkt.
     *
     * <p><strong>Mandantentrennung:</strong> über {@code userId} gebunden — die ID eines fremden
     * Users trifft nichts.
     *
     * @param userId ID des Users, dessen Benachrichtigung markiert wird.
     * @param notificationId ID der Benachrichtigung, wie {@link #create} sie geliefert hat.
     */
    void markRead(long userId, long notificationId);
}
