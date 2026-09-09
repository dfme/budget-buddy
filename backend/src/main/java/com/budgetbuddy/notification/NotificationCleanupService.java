package com.budgetbuddy.notification;

import org.springframework.stereotype.Service;

/**
 * Implementiert {@link NotificationCleanupPort} für die Kontolöschung (US-02, nDSG).
 *
 * <p>Der Repository-Aufruf läuft über ein {@code @Modifying}-Bulk-Delete und wird damit sofort
 * ausgeführt, nicht erst beim Flush der Transaktion (siehe
 * {@link NotificationRepository#deleteAllByUserId}) — Voraussetzung dafür, dass
 * {@code UserService.deleteUser} den User danach gefahrlos löschen kann.
 */
@Service
public class NotificationCleanupService implements NotificationCleanupPort {

    private final NotificationRepository notificationRepository;

    public NotificationCleanupService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void deleteAllForUser(long userId) {
        notificationRepository.deleteAllByUserId(userId);
    }
}
