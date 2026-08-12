package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.FixedCostRequest;
import com.budgetbuddy.budget.dto.FixedCostResponse;
import com.budgetbuddy.budget.dto.FixedCostSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CRUD-Endpoints für die Fixkosten des eingeloggten Users (BE-FC-03, US-03).
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird — {@code /fixed-costs} steht bewusst weder
 * in den {@code PUBLIC_PATHS} noch in {@code SpaForwardController.CLIENT_ROUTE_PATTERNS}.
 *
 * <p><strong>Kein {@code @Valid} an den Request-Bodys.</strong> Die fachlichen Regeln aus US-03
 * stehen vollständig im {@link FixedCostService} und nur dort (BE-FC-02-Entscheid); der
 * {@link FixedCostExceptionHandler} bildet sie auf 400 mit Feldnamen ab. Bean-Validation hier
 * hinzuzunehmen hiesse, dieselbe Regel an zwei Stellen zu pflegen.
 *
 * <p>Die Mandantentrennung liegt ebenfalls im Service bzw. im {@link FixedCostRepository}: dieser
 * Controller reicht nur die authentifizierte User-ID durch und trifft keine eigene Entscheidung
 * darüber, wer was sehen darf.
 */
@RestController
@RequestMapping("/fixed-costs")
@Tag(name = "Fixed Costs", description = "Fixkosten des eingeloggten Users (US-03)")
public class FixedCostController {

    private final FixedCostService fixedCostService;

    public FixedCostController(FixedCostService fixedCostService) {
        this.fixedCostService = fixedCostService;
    }

    @GetMapping
    @Operation(summary = "Alle Fixkosten des Users",
            description = "Liefert die Positionen inklusive normalisiertem Monatsbetrag, der "
                    + "monatlichen Gesamtsumme, dem erfassten Einkommen und dem Warn-Flag "
                    + "exceedsIncome (Fixkosten >= Einkommen, US-03).")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Übersicht zurückgegeben"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public FixedCostSummaryResponse list(@AuthenticationPrincipal Long userId) {
        return fixedCostService.list(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Einzelne Fixkosten-Position",
            description = "Liefert eine Position des eingeloggten Users.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Position zurückgegeben"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {}),
        @ApiResponse(responseCode = "404", description = "Keine Position dieser ID für den User",
                content = {})
    })
    public FixedCostResponse get(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "ID der Fixkosten-Position", example = "42")
            @PathVariable long id) {
        return fixedCostService.get(userId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Fixkosten-Position anlegen",
            description = "Legt eine Position an. intervall erwartet das ASCII-Label "
                    + "'monatlich', 'quartalsweise' oder 'jaehrlich'; betrag ist eine "
                    + "JSON-Zahl > 0 mit höchstens zwei Nachkommastellen. Die Antwort enthält "
                    + "die Einzelposition — das Warn-Flag exceedsIncome kommt über "
                    + "GET /fixed-costs.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Position angelegt"),
        @ApiResponse(responseCode = "400", description = "Feld fehlt oder ist ungültig; der Body "
                + "nennt das betroffene Feld"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public FixedCostResponse create(
            @AuthenticationPrincipal Long userId, @RequestBody FixedCostRequest request) {
        return fixedCostService.create(userId, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Fixkosten-Position ändern",
            description = "Überschreibt Bezeichnung, Betrag und Intervall einer Position. Der "
                    + "Besitzer wechselt dabei nie.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Position aktualisiert"),
        @ApiResponse(responseCode = "400", description = "Feld fehlt oder ist ungültig; der Body "
                + "nennt das betroffene Feld"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {}),
        @ApiResponse(responseCode = "404", description = "Keine Position dieser ID für den User",
                content = {})
    })
    public FixedCostResponse update(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "ID der Fixkosten-Position", example = "42")
            @PathVariable long id,
            @RequestBody FixedCostRequest request) {
        return fixedCostService.update(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Fixkosten-Position löschen",
            description = "Löscht eine Position des eingeloggten Users.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Position gelöscht", content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {}),
        @ApiResponse(responseCode = "404", description = "Keine Position dieser ID für den User",
                content = {})
    })
    public void delete(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "ID der Fixkosten-Position", example = "42")
            @PathVariable long id) {
        fixedCostService.delete(userId, id);
    }
}
