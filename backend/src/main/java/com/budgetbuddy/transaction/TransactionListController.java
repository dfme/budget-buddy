package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.TransactionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liste der einzelnen Ausgaben eines Monats (FE-CAT-03, US-05/US-13).
 *
 * <p>Liefert die Buchungen hinter den Summen des {@link TransactionSummaryController} — die
 * Kategorie-Übersicht klappt damit eine Kategorie auf und zeigt deren Transaktionen. Erst über die
 * hier enthaltene {@code id} ist {@code PUT /transactions/{id}/category} adressierbar.
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird. Teilt den Swagger-Tag {@code Transactions}
 * mit den übrigen Transaktions-Endpoints.
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Auswertungen über die Transaktionen des eingeloggten Users")
public class TransactionListController {

    private final TransactionListService listService;

    public TransactionListController(TransactionListService listService) {
        this.listService = listService;
    }

    @GetMapping
    @Operation(summary = "Ausgaben eines Monats auflisten",
            description = "Liefert die einzelnen Ausgaben des Monats, absteigend nach "
                    + "Buchungsdatum. Gutschriften sind nicht enthalten; nicht kategorisierte "
                    + "Transaktionen erscheinen als 'Sonstiges'. Mit 'category' lässt sich auf eine "
                    + "Kategorie eingrenzen — der Filter 'Sonstiges' trifft dabei auch die noch "
                    + "nicht kategorisierten Buchungen. Ein Label ohne Treffer liefert eine leere "
                    + "Liste, keinen Fehler.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Transaktionen zurückgegeben"),
        @ApiResponse(responseCode = "400", description = "month fehlt oder ist kein YYYY-MM",
                content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public List<TransactionResponse> listTransactions(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Monat im Format YYYY-MM, z. B. 2026-07", example = "2026-07")
            @RequestParam String month,
            @Parameter(description = "Optionaler Kategorie-Filter (deutsches Label)",
                    example = "Lebensmittel")
            @RequestParam(required = false) String category) {
        return listService.list(userId, month, category);
    }
}
