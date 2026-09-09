package com.budgetbuddy.notification;

import com.budgetbuddy.notification.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Benachrichtigungs-Endpoints für den eingeloggten User (BE-NOTIF-01, Fundament für US-08).
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird.
 *
 * <p>Unpaginiert — analog {@code TransactionDirectionController.listUncertain}: eine kleine
 * Inbox-Liste, keine Historie zum Blättern (US-08 verlangt bewusst kein Polling im MVP).
 *
 * <p>Die Mandantentrennung liegt im {@link NotificationService} bzw. im
 * {@link NotificationRepository}: dieser Controller reicht nur die authentifizierte User-ID durch.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Benachrichtigungen des eingeloggten Users")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "Benachrichtigungen des Users auflisten",
            description = "Liefert die Benachrichtigungen des eingeloggten Users, ungelesene "
                    + "zuerst und innerhalb dessen neueste zuerst. Ein User ohne Benachrichtigungen "
                    + "bekommt eine leere Liste, keinen Fehler.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste zurückgegeben, ggf. leer"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public List<NotificationResponse> list(@AuthenticationPrincipal Long userId) {
        return notificationService.list(userId);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Benachrichtigung als gelesen markieren",
            description = "Markiert eine Benachrichtigung des Users als gelesen und liefert ihren "
                    + "aktuellen Zustand. Idempotent — ein zweiter Aufruf ändert den ursprünglichen "
                    + "Lesezeitpunkt nicht.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Benachrichtigung aktualisiert"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {}),
        @ApiResponse(responseCode = "404",
                description = "Keine Benachrichtigung dieser ID für den User", content = {})
    })
    public NotificationResponse markAsRead(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "ID der Benachrichtigung", example = "42")
            @PathVariable long id) {
        return notificationService.markAsRead(userId, id);
    }
}
