package com.budgetbuddy.notification;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet die Exceptions des {@link NotificationController} auf HTTP-Status ab (BE-NOTIF-01).
 *
 * <p>Auf den Controller beschränkt statt global — gleiche Aufteilung wie beim
 * {@code FixedCostExceptionHandler}: {@link NotificationNotFoundException} ist ein
 * notification-eigener Typ, und ein globales Advice würde die Zuständigkeit über Modulgrenzen
 * ziehen.
 */
@RestControllerAdvice(assignableTypes = NotificationController.class)
public class NotificationExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleNotificationNotFound(NotificationNotFoundException ex) {
        // Kein Body: 404 ohne Auskunft, ob die ID existiert oder einem anderen User gehört.
    }
}
