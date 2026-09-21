package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.budgetbuddy.notification.NotificationPort;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit-Test der gemeinsamen Fehlschlags-Benachrichtigung (BE-PDF-16, #341).
 *
 * <p>Diese Klasse ist die eine Stelle, an der Typ und Text noch stehen — und damit die Stelle, an
 * der sie zu prüfen sind. {@link ImportJobRunnerTest} und {@link StaleImportJobCleanerTest} zeigen
 * nur noch, <em>dass</em> ihr Pfad hier vorbeikommt.
 */
class ImportFailureNotifierTest {

    private static final long USER_ID = 42L;

    private final NotificationPort notificationPort = mock(NotificationPort.class);
    private final ImportFailureNotifier notifier = new ImportFailureNotifier(notificationPort);

    /** AC1: Typ {@code IMPORT_FAILED}, {@code referenceId} = Job-ID, User des Jobs. */
    @Test
    void createsExactlyOneFailureNotificationForTheJobOwner() {
        ImportJob job = new ImportJob(USER_ID, "abc123", 20, Instant.parse("2026-09-04T12:00:00Z"));

        notifier.notifyFailed(job);

        ArgumentCaptor<String> message = ArgumentCaptor.forClass(String.class);
        verify(notificationPort).create(
                eq(USER_ID),
                eq(ImportFailureNotifier.NOTIFICATION_TYPE_FAILED),
                eq(job.getId()),
                message.capture());
        verifyNoMoreInteractions(notificationPort);
        assertThat(message.getValue()).isNotBlank();
    }

    /**
     * AC2: Ein Fehler beim Erzeugen der Benachrichtigung verlässt diese Methode nicht.
     *
     * <p>Der Aufrufer hat den {@code FAILED}-Status zu diesem Zeitpunkt bereits geschrieben. Käme
     * die Exception hier heraus, nähme sie ihm zwar den Status nicht mehr weg, brächte aber je
     * nach Aufrufer den Startlauf des Cleaners, die restliche Schleife oder die
     * {@code addSuppressed}-Behandlung im {@code Error}-Pfad des Runners durcheinander.
     */
    @Test
    void swallowsAFailingNotificationInsteadOfLettingItEscape() {
        doThrow(new IllegalStateException("Notification-Service kaputt"))
                .when(notificationPort).create(anyLong(), anyString(), any(), anyString());
        ImportJob job = new ImportJob(USER_ID, "abc123", 20, Instant.parse("2026-09-04T12:00:00Z"));

        assertThatCode(() -> notifier.notifyFailed(job)).doesNotThrowAnyException();
    }
}
