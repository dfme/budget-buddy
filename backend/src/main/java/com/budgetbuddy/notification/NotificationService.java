package com.budgetbuddy.notification;

import com.budgetbuddy.notification.dto.NotificationResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
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
 * <p><strong>Mandantentrennung:</strong> {@link #list(long)}, {@link #markAsRead(long, long)},
 * {@link #markAllAsRead(long)}, {@link #unreadIds(long, String)} und {@link #markRead(long, long)}
 * laufen ausschliesslich über die user-gebundenen Methoden des {@link NotificationRepository}.
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
    public long create(long userId, String type, Long referenceId, String message) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type darf nicht leer sein.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message darf nicht leer sein.");
        }
        return notificationRepository
                .save(new Notification(userId, type, referenceId, message, clock.instant()))
                .getId();
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
     * Markiert alle ungelesenen Benachrichtigungen des Users als gelesen und liefert die
     * vollständige Liste in derselben Reihenfolge wie {@link #list(long)} (FE-NOTIF-04) — der
     * Client ersetzt seinen Stand damit in einem Zug, statt {@code GET} nachzuschieben.
     *
     * <p>Idempotent und ohne Fehlerfall: bereits gelesene Benachrichtigungen behalten ihren
     * Lesezeitpunkt (siehe {@link Notification#markRead(java.time.Instant)}), ein User ohne
     * ungelesene bekommt schlicht seine Liste zurück. Ein 404 wie bei {@link #markAsRead} gäbe
     * es hier nicht zu melden — es gibt keine einzelne ID, die fehlen könnte.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     */
    @Transactional
    public List<NotificationResponse> markAllAsRead(long userId) {
        Instant now = clock.instant();
        for (Notification notification : notificationRepository.findByUserIdAndReadAtIsNull(userId)) {
            notification.markRead(now);
        }
        return list(userId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Nur gelesen, keine Mandantentrennung nötig über das hinaus, was {@code userId} in der
     * Query ohnehin einschränkt (kein einzelner Datensatz per ID abgefragt).
     */
    @Override
    @Transactional(readOnly = true)
    public Set<Long> unreadIds(long userId, String type) {
        return notificationRepository.findByUserIdAndTypeAndReadAtIsNull(userId, type).stream()
                .map(Notification::getId)
                .collect(Collectors.toSet());
    }

    /**
     * {@inheritDoc}
     *
     * <p><strong>Mandantentrennung:</strong> dieselbe user-gebundene Query wie
     * {@link #markAsRead}; eine fremde oder unbekannte ID trifft nichts und der Aufruf bleibt ein
     * No-op — anders als dort bewusst ohne Exception, weil hier kein Request-Client wartet.
     */
    @Override
    @Transactional
    public void markRead(long userId, long notificationId) {
        notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .ifPresent(notification -> notification.markRead(clock.instant()));
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
