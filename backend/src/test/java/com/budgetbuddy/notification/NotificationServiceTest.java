package com.budgetbuddy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.notification.dto.NotificationResponse;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit-Test des {@link NotificationService} (BE-NOTIF-01). Repository ist gemockt; der Pfad über
 * echtes PostgreSQL liegt in {@link NotificationRepositoryIntegrationTest} und
 * {@link NotificationControllerIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final long USER_ID = 42L;
    private static final long NOTIFICATION_ID = 7L;
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-07T10:15:00Z");

    @Mock private NotificationRepository notificationRepository;

    private final Clock clock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private NotificationService service;

    @BeforeEach
    void setUp() {
        // Kein @InjectMocks: Clock.fixed(...) ist kein Mockito-Mock und würde sonst nicht
        // injiziert. @Mock-Felder stehen erst nach dem Konstruktor — deshalb hier statt dort.
        service = new NotificationService(notificationRepository, clock);
    }

    // --- AC1: create() ---

    @Test
    void createPersistsWithTheGivenUserTypeReferenceAndMessage() {
        service.create(USER_ID, "RECURRING_EXPENSE_DETECTED", 99L, "Netflix erkannt");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getValue().getType()).isEqualTo("RECURRING_EXPENSE_DETECTED");
        assertThat(saved.getValue().getReferenceId()).isEqualTo(99L);
        assertThat(saved.getValue().getMessage()).isEqualTo("Netflix erkannt");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(saved.getValue().isRead()).isFalse();
    }

    @Test
    void createAcceptsANullReferenceId() {
        service.create(USER_ID, "MONTHLY_REPORT_READY", null, "Bericht bereit");

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());
        assertThat(saved.getValue().getReferenceId()).isNull();
    }

    @Test
    void rejectsNullType() {
        assertThatThrownBy(() -> service.create(USER_ID, null, null, "Nachricht"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsBlankType() {
        assertThatThrownBy(() -> service.create(USER_ID, "   ", null, "Nachricht"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsNullMessage() {
        assertThatThrownBy(() -> service.create(USER_ID, "TYPE", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> service.create(USER_ID, "TYPE", null, "   "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(notificationRepository, never()).save(any());
    }

    // --- AC2: list() ungelesene zuerst (Sortierung selbst gehört der Repository-Query) ---

    @Test
    void listMapsEntitiesToResponsesInTheOrderTheRepositoryReturnsThem() {
        Notification unread = entry(1L, "TYPE_A", 10L, "Erste", null);
        Notification read = entry(2L, "TYPE_B", null, "Zweite", FIXED_INSTANT.minusSeconds(60));
        when(notificationRepository.findByUserIdOrderByUnreadFirstThenNewest(USER_ID))
                .thenReturn(List.of(unread, read));

        List<NotificationResponse> responses = service.list(USER_ID);

        assertThat(responses).extracting(NotificationResponse::id).containsExactly(1L, 2L);
        assertThat(responses.get(0).read()).isFalse();
        assertThat(responses.get(1).read()).isTrue();
        assertThat(responses.get(0).type()).isEqualTo("TYPE_A");
        assertThat(responses.get(0).referenceId()).isEqualTo(10L);
    }

    @Test
    void listReturnsEmptyForAUserWithoutNotifications() {
        when(notificationRepository.findByUserIdOrderByUnreadFirstThenNewest(USER_ID))
                .thenReturn(List.of());

        assertThat(service.list(USER_ID)).isEmpty();
    }

    // --- AC3: markAsRead() ---

    @Test
    void markAsReadSetsReadAtAndReturnsTheUpdatedState() {
        Notification notification = entry(NOTIFICATION_ID, "TYPE", null, "Nachricht", null);
        when(notificationRepository.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.of(notification));

        NotificationResponse response = service.markAsRead(USER_ID, NOTIFICATION_ID);

        assertThat(response.read()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void markAsReadIsIdempotentAndKeepsTheOriginalReadTimestamp() {
        Instant firstRead = FIXED_INSTANT.minusSeconds(3600);
        Notification notification = entry(NOTIFICATION_ID, "TYPE", null, "Nachricht", firstRead);
        when(notificationRepository.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.of(notification));

        NotificationResponse response = service.markAsRead(USER_ID, NOTIFICATION_ID);

        assertThat(response.read()).isTrue();
        assertThat(notification.getReadAt()).isEqualTo(firstRead); // unverändert, nicht FIXED_INSTANT
    }

    @Test
    void markAsReadThrowsNotFoundWhenTheEntryIsMissingOrForeign() {
        when(notificationRepository.findByIdAndUserId(NOTIFICATION_ID, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(USER_ID, NOTIFICATION_ID))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    // --- Helfer ---

    /**
     * Baut eine {@link Notification} mit gesetzter ID und optional bereits gesetztem
     * {@code readAt}. Die ID vergibt sonst die Datenbank; das Entity hat dafür bewusst keinen
     * Setter, deshalb Reflection — analog {@code FixedCostServiceTest}.
     */
    private static Notification entry(
            long id, String type, Long referenceId, String message, Instant readAt) {
        Notification notification =
                new Notification(USER_ID, type, referenceId, message, FIXED_INSTANT.minusSeconds(120));
        try {
            Field idField = Notification.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(notification, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        if (readAt != null) {
            notification.markRead(readAt);
        }
        return notification;
    }
}
