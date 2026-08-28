package com.budgetbuddy.budget;

import org.springframework.stereotype.Service;

/**
 * Implementiert {@link FixedCostCleanupPort} für die Kontolöschung (US-02, DB-07).
 *
 * <p>Der Repository-Aufruf läuft über ein {@code @Modifying}-Bulk-Delete und wird damit sofort
 * ausgeführt, nicht erst beim Flush der Transaktion (siehe {@link FixedCostRepository#deleteAllByUserId}).
 * Das ist die Voraussetzung dafür, dass {@code UserService.deleteUser} den User danach gefahrlos
 * löschen kann, ohne auf Hibernates interne Flush-Reihenfolge zu vertrauen.
 */
@Service
public class FixedCostCleanupService implements FixedCostCleanupPort {

    private final FixedCostRepository fixedCostRepository;

    public FixedCostCleanupService(FixedCostRepository fixedCostRepository) {
        this.fixedCostRepository = fixedCostRepository;
    }

    @Override
    public void deleteAllForUser(long userId) {
        fixedCostRepository.deleteAllByUserId(userId);
    }
}
