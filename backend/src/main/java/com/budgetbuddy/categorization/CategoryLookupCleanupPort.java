package com.budgetbuddy.categorization;

/**
 * Schreib-Port für die Kontolöschung (US-02, nDSG): löscht alle gelernten Händler-Patterns eines
 * Users aus {@code user_category_lookup} (BE-CAT-12, ADR-15).
 *
 * <p>Über dieses Interface räumt {@code UserService.deleteUser} auf, ohne direkt auf
 * {@link UserCategoryLookupRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart
 * wie {@code TransactionCleanupPort}/{@code FixedCostCleanupPort}/{@code NotificationCleanupPort}/
 * {@code RecurringExpenseCleanupPort}. Die globale {@code category_lookup}-Tabelle (V04) bleibt
 * unberührt — sie hält keine Nutzerdaten mehr, nur die kuratierten Seeds.
 */
public interface CategoryLookupCleanupPort {

    /**
     * Löscht alle {@code user_category_lookup}-Zeilen dieses Users.
     *
     * <p>Muss vor dem Löschen des Users selbst aufgerufen werden — der Fremdschlüssel auf
     * {@code users} steht bewusst ohne {@code ON DELETE CASCADE} (V12, DB-07).
     *
     * @param userId ID des zu löschenden Users.
     */
    void deleteAllForUser(long userId);
}
