package com.budgetbuddy.notification;

import com.budgetbuddy.notification.dto.NotificationResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Erzeugen, Auflisten und Als-gelesen-Markieren von Benachrichtigungen (BE-NOTIF-01).
 *
 * <p>Implementiert den {@link NotificationPort}, über den andere Module Benachrichtigungen
 * erzeugen (Fundament für US-08) — kein Aufrufer existiert in diesem Issue, der Port hält die
 * Modulgrenze trotzdem von Anfang an ein (CLAUDE.md).
 *
 * <p><strong>Mandantentrennung:</strong> {@link #list(long)}, {@link #markAsRead(long, long)} und
 * {@link #markReadByReference(long, String, long)} laufen ausschliesslich über die
 * user-gebundenen Methoden des {@link NotificationRepository}.
 *
 * <p>{@code createdAt}/{@code readAt} kommen aus der injizierten {@link Clock} (analog
 * {@code ImportJob}), nicht aus {@code Instant.now()} — deterministisch testbar.
 */
@Service
public class NotificationService implements NotificationPort {

    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Wirft {@link IllegalArgumentException} bei leerem {@code type}/{@code message} —
     * Guard Clauses statt der 400-mit-Feldname-Kette anderer Module: dieser Port wird nur
     * modulintern von Code aufgerufen, nie direkt aus einem Request-Body.
     */
    @Override
    @Transactional
    public void create(long userId, String type, Long referenceId, String message) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type darf nicht leer sein.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message darf nicht leer sein.");
        }
        notificationRepository.save(
                new Notification(userId, type, referenceId, message, clock.instant()));
    }

    /**
     * Liefert die Benachrichtigungen des Users, ungelesene zuerst.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     */
    @Transactional(readOnly = true)
    public List<NotificationResponse> list(long userId) {
        return notificationRepository.findByUserIdOrderByUnreadFirstThenNewest(userId).stream()
                .map(NotificationService::toResponse)
                .toList();
    }

    /**
     * Markiert eine Benachrichtigung des Users als gelesen und liefert ihren aktuellen Zustand.
     *
     * <p>Idempotent: ein zweiter Aufruf ändert den bereits gesetzten Lesezeitpunkt nicht (siehe
     * {@link Notification#markRead(java.time.Instant)}).
     *
     * @throws NotificationNotFoundException wenn die ID nicht existiert oder einem anderen User
     *     gehört.
     */
    @Transactional
    public NotificationResponse markAsRead(long userId, long notificationId) {
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(userId, notificationId));
        notification.markRead(clock.instant());
        return toResponse(notification);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Nur gelesen, keine Mandantentrennung nötig über das hinaus, was {@code userId} in der
     * Query ohnehin einschränkt (kein einzelner Datensatz per ID abgefragt).
     */
    @Override
    @Transactional(readOnly = true)
    public Set<Long> unreadReferenceIds(long userId, String type) {
        return notificationRepository.findByUserIdAndTypeAndReadAtIsNull(userId, type).stream()
                .map(Notification::getReferenceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Mandantentrennung:</strong> die Query ist über {@code userId} eingeschränkt;
     * ein fremder Verweis trifft nichts und der Aufruf bleibt ein No-op.
     */
    @Override
    @Transactional
    public void markReadByReference(long userId, String type, long referenceId) {
        Instant now = clock.instant();
        for (Notification notification
                : notificationRepository.findByUserIdAndTypeAndReferenceIdAndReadAtIsNull(
                        userId, type, referenceId)) {
            notification.markRead(now);
        }
    }

    private static NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getReferenceId(),
                notification.getMessage(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
