package com.budgetbuddy.budget;

/**
 * Schreib-Port für die Kontolöschung (US-02, DB-07): löscht alle Fixkosten-Positionen eines Users.
 *
 * <p>Über dieses Interface räumt {@code UserService.deleteUser} auf, ohne direkt auf
 * {@link FixedCostRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart wie
 * {@code UserIncomePort}/{@code IncomeSuggestionPort}: das Interface steht im <em>liefernden</em>
 * Modul, nicht im aufrufenden.
 */
public interface FixedCostCleanupPort {

    /**
     * Löscht alle {@code fixed_costs}-Zeilen dieses Users.
     *
     * <p>Muss vor dem Löschen des Users selbst aufgerufen werden — sonst schlägt dessen Löschung
     * am Fremdschlüssel fehl.
     *
     * @param userId ID des zu löschenden Users.
     */
    void deleteAllForUser(long userId);
}
