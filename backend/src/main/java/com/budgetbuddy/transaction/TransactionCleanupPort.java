package com.budgetbuddy.transaction;

/**
 * Schreib-Port für die Kontolöschung (US-02, DB-07): löscht alle Daten eines Users, die im
 * {@code transaction}-Modul liegen.
 *
 * <p>Über dieses Interface räumt {@code UserService.deleteUser} auf, ohne direkt auf
 * {@link TransactionRepository} oder {@link ImportJobRepository} zuzugreifen (Modulgrenze, siehe
 * CLAUDE.md). Gleiche Bauart wie {@code UserIncomePort}/{@code IncomeSuggestionPort}: das
 * Interface steht im <em>liefernden</em> Modul, nicht im aufrufenden.
 *
 * <p>Beide Tabellen — {@code transactions} und {@code import_jobs} — tragen eine Fremdschlüssel
 * auf {@code users} ohne {@code ON DELETE} (DB-07). Beide leben im transaction-Modul, deshalb ein
 * gemeinsamer Port statt zweier: der Aufrufer braucht keine Kenntnis davon, dass hinter «den
 * Transaktionsdaten des Users» zwei Tabellen stehen.
 */
public interface TransactionCleanupPort {

    /**
     * Löscht alle {@code transactions}- und {@code import_jobs}-Zeilen dieses Users.
     *
     * <p>Muss vor dem Löschen des Users selbst aufgerufen werden — sonst schlägt dessen Löschung
     * am Fremdschlüssel fehl.
     *
     * @param userId ID des zu löschenden Users.
     */
    void deleteAllForUser(long userId);
}
