package com.budgetbuddy.recurring;

import com.budgetbuddy.recurring.dto.RecurringExpenseResponse;
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
 * Abo-Übersicht-Endpoints für den eingeloggten User (BE-REC-02, US-08).
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird.
 *
 * <p>Unpaginiert — analog {@code NotificationController}: eine kleine Übersicht, keine Historie
 * zum Blättern.
 *
 * <p>Die Mandantentrennung liegt im {@link RecurringExpenseService} bzw. im
 * {@link RecurringExpenseRepository}: dieser Controller reicht nur die authentifizierte User-ID
 * durch.
 */
@RestController
@RequestMapping("/api/recurring-expenses")
@Tag(name = "Recurring Expenses", description = "Abo-Übersicht des eingeloggten Users")
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    public RecurringExpenseController(RecurringExpenseService recurringExpenseService) {
        this.recurringExpenseService = recurringExpenseService;
    }

    @GetMapping
    @Operation(summary = "Abo-Übersicht des Users auflisten",
            description = "Liefert die wiederkehrenden Ausgaben des eingeloggten Users in beiden "
                    + "Status (DETECTED und DISMISSED), alphabetisch nach Empfänger, inkl. "
                    + "Neu-Flag. Ein per Kein-Abo markierter Eintrag bleibt mit status=DISMISSED "
                    + "enthalten — die Übersicht zeigt ihn in einem eigenen Abschnitt. Ein User "
                    + "ohne Einträge bekommt eine leere Liste, keinen Fehler.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste zurückgegeben, ggf. leer"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public List<RecurringExpenseResponse> list(@AuthenticationPrincipal Long userId) {
        return recurringExpenseService.list(userId);
    }

    @PostMapping("/{id}/dismiss")
    @Operation(summary = "Eintrag als Kein Abo markieren",
            description = "Markiert einen Eintrag des Users als Kein Abo (status=DISMISSED) und "
                    + "liefert seinen aktuellen Zustand. Der zugehörige Empfänger wird künftig "
                    + "nicht mehr automatisch erkannt; die zugehörige Benachrichtigung "
                    + "(RECURRING_EXPENSE_DETECTED) gilt als gelesen, isNew ist in der Antwort "
                    + "immer false. Idempotent — ein zweiter Aufruf ändert den Status nicht "
                    + "erneut.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Eintrag aktualisiert"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {}),
        @ApiResponse(responseCode = "404",
                description = "Kein Abo-Eintrag dieser ID für den User", content = {})
    })
    public RecurringExpenseResponse dismiss(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "ID des Abo-Eintrags", example = "42") @PathVariable long id) {
        return recurringExpenseService.dismiss(userId, id);
    }
}
