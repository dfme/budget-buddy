package com.budgetbuddy.notification;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationCleanupService notificationCleanupService;

    @Test
    void deleteAllForUserDeletesNotifications() {
        notificationCleanupService.deleteAllForUser(42L);

        verify(notificationRepository).deleteAllByUserId(42L);
        verifyNoMoreInteractions(notificationRepository);
    }
}
