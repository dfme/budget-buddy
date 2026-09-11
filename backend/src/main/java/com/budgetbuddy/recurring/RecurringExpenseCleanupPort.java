package com.budgetbuddy.recurring;

/**
 * Schreib-Port für die Kontolöschung (US-02, nDSG): löscht alle erkannten wiederkehrenden
 * Ausgaben eines Users.
 *
 * <p>Über dieses Interface räumt {@code UserService.deleteUser} auf, ohne direkt auf
 * {@link RecurringExpenseRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart
 * wie {@code TransactionCleanupPort}/{@code FixedCostCleanupPort}/{@code NotificationCleanupPort}.
 */
public interface RecurringExpenseCleanupPort {

    /**
     * Löscht alle {@code recurring_expenses}-Zeilen dieses Users.
     *
     * <p>Muss vor dem Löschen des Users selbst aufgerufen werden — der Fremdschlüssel auf
     * {@code users} steht bewusst ohne {@code ON DELETE CASCADE} (V11, DB-07).
     *
     * @param userId ID des zu löschenden Users.
     */
    void deleteAllForUser(long userId);
}
