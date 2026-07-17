package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.CategorySummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Kategorie-Summary der Transaktionen (BE-CAT-05).
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird.
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Auswertungen über die Transaktionen des eingeloggten Users")
public class TransactionSummaryController {

    private final TransactionSummaryService summaryService;

    public TransactionSummaryController(TransactionSummaryService summaryService) {
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Ausgaben-Summary pro Kategorie",
            description = "Liefert für den angegebenen Monat je Kategorie die CHF-Summe, die Anzahl "
                    + "Transaktionen und den Prozentanteil an den Gesamtausgaben. Einbezogen werden "
                    + "nur Ausgaben (keine Gutschriften); nicht kategorisierte Transaktionen zählen "
                    + "als 'Sonstiges'. Die Prozentangaben summieren sich exakt auf 100.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Summary zurückgegeben"),
        @ApiResponse(responseCode = "400", description = "month fehlt oder ist kein YYYY-MM",
                content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public CategorySummaryResponse getSummary(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Monat im Format YYYY-MM, z. B. 2026-07", example = "2026-07")
            @RequestParam String month) {
        return summaryService.summarize(userId, month);
    }
}
