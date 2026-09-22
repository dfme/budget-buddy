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
     * jede <em>neu</em> erkannte einen Eintrag an und benachrichtigt den User.
     *
     * <p>Seit BE-REC-04 (#350) bewertet derselbe Lauf auch die <em>bestehenden</em> Einträge neu:
     * Betrag und Erstmonat folgen dem jüngsten qualifizierenden Paar, und eine Reihe ohne
     * Abbuchung in den jüngsten Monaten der Historie wird {@code ENDED} und mindert den
     * Safe-to-Spend nicht mehr. Diese Neubewertung erzeugt keine Benachrichtigung — der
     * «Neu»-Hinweis gilt dem Fund eines Abos, nicht seiner Preisänderung.
     *
     * <p>Als «Kein Abo» markierte Empfänger ({@code DISMISSED}) bleiben als einzige vollständig
     * unberührt (US-08 AC3).
     *
     * <p>Darf eine {@link RuntimeException} werfen — der Aufrufer isoliert sie, damit ein Fehler
     * in der Erkennung den Import nicht auf {@code FAILED} setzt.
     *
     * @param userId ID des Users, dessen Import gerade abgeschlossen wurde.
     */
    void detect(long userId);
}
