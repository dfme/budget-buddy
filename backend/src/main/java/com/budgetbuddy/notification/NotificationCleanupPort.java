package com.budgetbuddy.notification;

/**
 * Schreib-Port für die Kontolöschung (US-02, nDSG): löscht alle Benachrichtigungen eines Users.
 *
 * <p>Über dieses Interface räumt {@code UserService.deleteUser} auf, ohne direkt auf
 * {@link NotificationRepository} zuzugreifen (Modulgrenze, siehe CLAUDE.md). Gleiche Bauart wie
 * {@code FixedCostCleanupPort}/{@code TransactionCleanupPort}.
 */
public interface NotificationCleanupPort {

    /**
     * Löscht alle {@code notifications}-Zeilen dieses Users.
     *
     * <p>Muss vor dem Löschen des Users selbst aufgerufen werden — sonst schlägt dessen Löschung
     * am Fremdschlüssel fehl.
     *
     * @param userId ID des zu löschenden Users.
     */
    void deleteAllForUser(long userId);
}
