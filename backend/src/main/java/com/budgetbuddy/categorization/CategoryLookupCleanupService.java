package com.budgetbuddy.categorization;

import org.springframework.stereotype.Service;

/**
 * Implementiert {@link CategoryLookupCleanupPort} für die Kontolöschung (US-02, nDSG).
 *
 * <p>Der Repository-Aufruf läuft über ein {@code @Modifying}-Bulk-Delete und wird damit sofort
 * ausgeführt, nicht erst beim Flush der Transaktion (siehe
 * {@link UserCategoryLookupRepository#deleteAllByUserId}) — Voraussetzung dafür, dass
 * {@code UserService.deleteUser} den User danach gefahrlos löschen kann.
 */
@Service
public class CategoryLookupCleanupService implements CategoryLookupCleanupPort {

    private final UserCategoryLookupRepository userCategoryLookupRepository;

    public CategoryLookupCleanupService(UserCategoryLookupRepository userCategoryLookupRepository) {
        this.userCategoryLookupRepository = userCategoryLookupRepository;
    }

    @Override
    public void deleteAllForUser(long userId) {
        userCategoryLookupRepository.deleteAllByUserId(userId);
    }
}
