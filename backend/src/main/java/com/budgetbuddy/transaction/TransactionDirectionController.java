package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.TransactionResponse;
import com.budgetbuddy.transaction.dto.UpdateDirectionRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Buchungen mit ungeprüfter Richtung auflisten und korrigieren (BE-PDF-10, US-04).
 *
 * <p>Beide Endpoints gehören zusammen: Der erste liefert die offenen Fälle eines Monats, der
 * zweite nimmt die Entscheidung des Nutzers dazu entgegen. Getrennt vom
 * {@link TransactionCategoryController}, weil es eine andere Korrektur ist — die Kategorie wird
 * gelernt und gilt beim nächsten Händler wieder, die Richtung gilt nur für diese eine Buchung.
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird. Teilt den Swagger-Tag {@code Transactions}
 * mit den übrigen Transaktions-Endpoints.
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Auswertungen über die Transaktionen des eingeloggten Users")
public class TransactionDirectionController {

    private final TransactionDirectionService directionService;

    public TransactionDirectionController(TransactionDirectionService directionService) {
        this.directionService = directionService;
    }

    @GetMapping("/uncertain")
    @Operation(summary = "Buchungen mit ungeprüfter Richtung auflisten",
            description = "Liefert die Buchungen des Monats, deren Richtung der PDF-Parser nicht "
                    + "eindeutig aus dem Saldo ableiten konnte und die er deshalb konservativ als "
                    + "Belastung übernommen hat — neueste zuerst. Ist eine Gutschrift darunter, "
                    + "ist ihr Vorzeichen gedreht und Safe-to-Spend fällt zu tief aus; über "
                    + "PUT /transactions/{id}/direction lässt sich das richtigstellen. Der "
                    + "Normalfall ist eine leere Liste. Nicht paginiert: Das ist eine "
                    + "Aufgabenliste, die beim Abarbeiten schrumpft, keine Historie zum Blättern.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste zurückgegeben, ggf. leer"),
        @ApiResponse(responseCode = "400", description = "month fehlt oder ist kein YYYY-MM",
                content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public List<TransactionResponse> listUncertain(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Monat im Format YYYY-MM, z. B. 2026-07", example = "2026-07")
            @RequestParam String month) {
        return directionService.listUncertain(userId, month);
    }

    @PutMapping("/{id}/direction")
    @Operation(summary = "Buchungsrichtung einer Transaktion setzen",
            description = "Setzt die Richtung der Transaktion auf Gutschrift (income=true) oder "
                    + "Belastung (income=false) und markiert sie damit als geprüft — auch dann, "
                    + "wenn die angenommene Richtung bloss bestätigt wird. Die Buchung "
                    + "verschwindet dadurch aus GET /transactions/uncertain. Eine auf Gutschrift "
                    + "gesetzte Buchung zählt ab sofort nicht mehr zu den Ausgaben und hebt damit "
                    + "den Safe-to-Spend des betroffenen Monats.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Richtung aktualisiert"),
        @ApiResponse(responseCode = "400", description = "income fehlt", content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {}),
        @ApiResponse(responseCode = "404", description = "Keine Transaktion dieser ID für den User",
                content = {})
    })
    public TransactionResponse updateDirection(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "ID der Transaktion", example = "42") @PathVariable long id,
            @Valid @RequestBody UpdateDirectionRequest request) {
        return directionService.updateDirection(userId, id, request.income());
    }
}
