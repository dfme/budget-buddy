package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.TransactionResponse;
import com.budgetbuddy.transaction.dto.UpdateCategoryRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manuelle Kategorie-Korrektur einer Transaktion (BE-CAT-04, US-05).
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird. Teilt den Swagger-Tag {@code Transactions}
 * mit dem {@link TransactionSummaryController}.
 */
@RestController
@RequestMapping("/transactions")
@Tag(name = "Transactions", description = "Auswertungen über die Transaktionen des eingeloggten Users")
public class TransactionCategoryController {

    private final TransactionCategoryService categoryService;

    public TransactionCategoryController(TransactionCategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PutMapping("/{id}/category")
    @Operation(summary = "Kategorie einer Transaktion manuell setzen",
            description = "Aktualisiert die Kategorie der Transaktion und lernt den Händlertext als "
                    + "Lookup-Pattern, sodass die nächste Transaktion desselben Händlers ohne "
                    + "Claude-Call kategorisiert wird. Erwartet ein gültiges deutsches "
                    + "Kategorie-Label (z. B. 'Lebensmittel').")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kategorie aktualisiert"),
        @ApiResponse(responseCode = "400", description = "category fehlt oder ist ungültig",
                content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {}),
        @ApiResponse(responseCode = "404", description = "Keine Transaktion dieser ID für den User",
                content = {})
    })
    public TransactionResponse updateCategory(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "ID der Transaktion", example = "42") @PathVariable long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(userId, id, request.category());
    }
}
