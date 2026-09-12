package com.budgetbuddy.recurring;

/**
 * Schreib-Port, über den der Import-Flow die Abo-Erkennung anstösst (BE-REC-01, US-08).
 *
 * <p>Gleiche Bauart wie {@code NotificationPort}/{@code CategorizationPort}: das Interface steht
 * im <em>liefernden</em> Modul und wird von {@link RecurringExpenseService} implementiert.
 * Aufrufer ist {@code ImportJobRunner} im transaction-Modul, am Ende eines erfolgreichen Imports.
 * Die Gegenrichtung — recurring liest Transaktionen — läuft über
 * {@code com.budgetbuddy.transaction.ExpenseHistoryPort}; der Zyklus zwischen den beiden Modulen
 * besteht nur aus Interfaces, wie heute schon zwischen auth und budget.
 */
public interface RecurringExpenseDetectionPort {

    /**
     * Sucht in der gesamten Ausgaben-Historie des Users nach wiederkehrenden Ausgaben, legt für
     * jede <em>neu</em> erkannte einen {@code DETECTED}-Eintrag an und benachrichtigt den User.
     * Bereits bekannte oder als «Kein Abo» markierte Empfänger bleiben unberührt.
     *
     * <p>Darf eine {@link RuntimeException} werfen — der Aufrufer isoliert sie, damit ein Fehler
     * in der Erkennung den Import nicht auf {@code FAILED} setzt.
     *
     * @param userId ID des Users, dessen Import gerade abgeschlossen wurde.
     */
    void detect(long userId);
}
