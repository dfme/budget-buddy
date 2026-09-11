package com.budgetbuddy.recurring;

import org.springframework.stereotype.Service;

/**
 * Implementiert {@link RecurringExpenseCleanupPort} für die Kontolöschung (US-02, nDSG).
 *
 * <p>Der Repository-Aufruf läuft über ein {@code @Modifying}-Bulk-Delete und wird damit sofort
 * ausgeführt, nicht erst beim Flush der Transaktion (siehe
 * {@link RecurringExpenseRepository#deleteAllByUserId}) — Voraussetzung dafür, dass
 * {@code UserService.deleteUser} den User danach gefahrlos löschen kann.
 */
@Service
public class RecurringExpenseCleanupService implements RecurringExpenseCleanupPort {

    private final RecurringExpenseRepository recurringExpenseRepository;

    public RecurringExpenseCleanupService(RecurringExpenseRepository recurringExpenseRepository) {
        this.recurringExpenseRepository = recurringExpenseRepository;
    }

    @Override
    public void deleteAllForUser(long userId) {
        recurringExpenseRepository.deleteAllByUserId(userId);
    }
}
