package com.budgetbuddy.transaction;

import org.springframework.stereotype.Service;

/**
 * Implementiert {@link TransactionCleanupPort} für die Kontolöschung (US-02, DB-07).
 *
 * <p>Beide Repository-Aufrufe laufen über {@code @Modifying}-Bulk-Deletes und werden damit sofort
 * ausgeführt, nicht erst beim Flush der Transaktion (siehe {@link TransactionRepository#deleteAllByUserId}).
 * Das ist die Voraussetzung dafür, dass {@code UserService.deleteUser} den User danach gefahrlos
 * löschen kann, ohne auf Hibernates interne Flush-Reihenfolge zu vertrauen.
 */
@Service
public class TransactionCleanupService implements TransactionCleanupPort {

    private final TransactionRepository transactionRepository;
    private final ImportJobRepository importJobRepository;

    public TransactionCleanupService(
            TransactionRepository transactionRepository, ImportJobRepository importJobRepository) {
        this.transactionRepository = transactionRepository;
        this.importJobRepository = importJobRepository;
    }

    @Override
    public void deleteAllForUser(long userId) {
        transactionRepository.deleteAllByUserId(userId);
        importJobRepository.deleteAllByUserId(userId);
    }
}
