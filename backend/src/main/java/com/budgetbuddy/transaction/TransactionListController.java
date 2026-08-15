package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.TransactionListResponse;
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
    @Operation(summary = "Ausgaben eines Monats seitenweise auflisten",
            description = "Liefert eine Seite der einzelnen Ausgaben des Monats, absteigend nach "
                    + "Buchungsdatum. Gutschriften sind nicht enthalten; nicht kategorisierte "
                    + "Transaktionen erscheinen als 'Sonstiges'. Mit 'category' lässt sich auf eine "
                    + "Kategorie eingrenzen — der Filter 'Sonstiges' trifft dabei auch die noch "
                    + "nicht kategorisierten Buchungen. Ein Label ohne Treffer liefert eine leere "
                    + "Liste, keinen Fehler. Ohne 'page' und 'size' werden die ersten "
                    + TransactionListService.DEFAULT_PAGE_SIZE + " Buchungen geliefert; 'hasMore' "
                    + "in der Antwort sagt, ob dahinter weitere folgen.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Seite der Transaktionen zurückgegeben"),
        @ApiResponse(responseCode = "400",
                description = "month fehlt oder ist kein YYYY-MM; page negativ; size ausserhalb "
                        + "1.." + TransactionListService.MAX_PAGE_SIZE,
                content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public TransactionListResponse listTransactions(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Monat im Format YYYY-MM, z. B. 2026-07", example = "2026-07")
            @RequestParam String month,
            @Parameter(description = "Optionaler Kategorie-Filter (deutsches Label)",
                    example = "Lebensmittel")
            @RequestParam(required = false) String category,
            @Parameter(description = "Nullbasierte Seitennummer; Standard 0", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Buchungen pro Seite, 1 bis "
                    + TransactionListService.MAX_PAGE_SIZE + "; Standard "
                    + TransactionListService.DEFAULT_PAGE_SIZE, example = "20")
            @RequestParam(defaultValue = TransactionListService.DEFAULT_PAGE_SIZE) int size) {
        return listService.list(userId, month, category, page, size);
    }

    @GetMapping("/months")
    @Operation(summary = "Monate mit Ausgaben auflisten",
            description = "Liefert die Monate im Format YYYY-MM, in denen der eingeloggte User "
                    + "Ausgaben hat — neuester zuerst. Eingabe des Monats-Dropdowns der "
                    + "Kategorie-Übersicht, das damit nur Monate anbietet, in denen auch etwas zu "
                    + "sehen ist. Monate mit ausschliesslich Gutschriften erscheinen nicht. Ein "
                    + "User ohne Ausgaben bekommt eine leere Liste, keinen Fehler.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Monate zurückgegeben"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public List<String> listMonths(@AuthenticationPrincipal Long userId) {
        return listService.availableMonths(userId);
    }
}
