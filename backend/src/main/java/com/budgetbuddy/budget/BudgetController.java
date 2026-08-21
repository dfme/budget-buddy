package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.SafeToSpendResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Budget-Endpoints des eingeloggten Users (BE-STS-03, US-06).
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird — {@code /budget} steht bewusst weder in den
 * {@code PUBLIC_PATHS} noch in {@code SpaForwardController.CLIENT_ROUTE_PATTERNS}. Eine Ergänzung
 * dort wäre eine Freigabe, keine Absicherung.
 *
 * <p>Die Berechnung selbst steht vollständig im {@link SafeToSpendService} und nur dort — Formel,
 * Divisor, Zeitzone und die Bedeutung der beiden Zustands-Flags sind in dessen Javadoc begründet.
 * Dieser Controller reicht die authentifizierte User-ID durch und trifft keine eigene Entscheidung
 * darüber, wer was sehen darf; die Mandantentrennung liegt im Service, der ausschliesslich
 * user-gebundene Ports liest.
 */
@RestController
@RequestMapping("/api/budget")
@Tag(name = "Budget", description = "Safe-to-Spend des eingeloggten Users (US-06)")
public class BudgetController {

    private final SafeToSpendService safeToSpendService;

    public BudgetController(SafeToSpendService safeToSpendService) {
        this.safeToSpendService = safeToSpendService;
    }

    @GetMapping("/safe-to-spend")
    @Operation(summary = "Wöchentlicher Safe-to-Spend-Betrag",
            description = "Liefert den Betrag, den der User in jeder verbleibenden Woche des "
                    + "laufenden Monats noch ausgeben kann: (Monatseinkommen − monatliche "
                    + "Fixkosten − Ausgaben des laufenden Monats) ÷ weeksLeft. weeksLeft sind die "
                    + "verbleibenden Wochen inklusive heute, aufgerundet und mindestens 1.\n\n"
                    + "Die Antwort hat drei Zustände:\n"
                    + "- **Normalfall:** amount ist gesetzt, negative und noIncome sind false.\n"
                    + "- **Budget überzogen:** amount ist negativ und negative ist true — der "
                    + "Client zeigt das Warn-Banner aus US-06.\n"
                    + "- **Kein Einkommen erfasst:** noIncome ist true und amount ist null, weil "
                    + "keine Division stattfindet. null ist damit von '0.00 übrig' "
                    + "unterscheidbar. Liess sich aus den wiederkehrenden Gutschriften ein "
                    + "Einkommen ableiten, steht der Vorschlag in incomeSuggestion; sonst ist "
                    + "auch der null. Bei erfasstem Einkommen ist incomeSuggestion immer null.\n\n"
                    + "Alle Beträge sind CHF mit zwei Nachkommastellen.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Safe-to-Spend zurückgegeben"),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public SafeToSpendResponse safeToSpend(@AuthenticationPrincipal Long userId) {
        return safeToSpendService.calculate(userId);
    }
}
