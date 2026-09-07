package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.SafeToSpendResponse;
import com.budgetbuddy.transaction.MonthParser;
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
 *
 * <p>Der {@code month}-Parameter (BE-STS-06) wird über den {@link MonthParser} des
 * transaction-Moduls gelesen — denselben, den {@code GET /api/transactions} und
 * {@code GET /api/transactions/summary} verwenden. Ein zweiter Parser hiesse, dass zwei Endpoints
 * desselben Frontends dieselbe Zeichenkette unterschiedlich auslegen könnten. Die daraus
 * entstehende {@code InvalidMonthException} bildet der {@link BudgetExceptionHandler} auf 400 ab;
 * dass dafür ein eigenes Advice nötig ist, ist dort begründet.
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
                    + "Fixkosten − variable Ausgaben des laufenden Monats) ÷ weeksLeft. weeksLeft "
                    + "sind die verbleibenden Wochen inklusive heute, aufgerundet und mindestens "
                    + "1.\n\n"
                    + "Variable Ausgaben sind die Belastungen des Monats abzüglich der per "
                    + "Dauerauftrag bezahlten Fixkosten: je erfasster Fixkosten-Position wird "
                    + "höchstens eine betragsgleiche Belastung ausgenommen, damit die Position "
                    + "den Betrag genau einmal mindert (ADR-13). In der Kategorie-Übersicht "
                    + "bleibt diese Belastung sichtbar.\n\n"
                    + "Die Antwort hat drei Zustände:\n"
                    + "- **Normalfall:** amount ist gesetzt, negative und noIncome sind false.\n"
                    + "- **Budget überzogen:** amount ist negativ und negative ist true — der "
                    + "Client zeigt das Warn-Banner aus US-06.\n"
                    + "- **Kein Einkommen erfasst:** noIncome ist true und amount ist null, weil "
                    + "keine Division stattfindet. null ist damit von '0.00 übrig' "
                    + "unterscheidbar. Liess sich aus den wiederkehrenden Gutschriften ein "
                    + "Einkommen ableiten, steht der Vorschlag in incomeSuggestion; sonst ist "
                    + "auch der null. Bei erfasstem Einkommen ist incomeSuggestion immer null.\n\n"
                    + "Alle Beträge sind CHF mit zwei Nachkommastellen.\n\n"
                    + "**Monat (US-12).** Ohne month-Parameter gilt der laufende Monat. Für einen "
                    + "vergangenen Monat wird nicht gerechnet: die Antwort trägt dann "
                    + "status=CLOSED, amount und incomeSuggestion sind null, weeksLeft ist 0 und "
                    + "beide Flags sind false — der Client zeigt 'Abgeschlossen'. Im laufenden "
                    + "Monat ist status=OPEN. Ein Monat in der Zukunft wird mit 400 abgelehnt, "
                    + "weil ein Wochenbudget für einen noch nicht begonnenen Monat nicht "
                    + "definiert ist.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Safe-to-Spend zurückgegeben"),
        @ApiResponse(responseCode = "400",
                description = "month ist kein YYYY-MM oder liegt nach dem laufenden Monat",
                content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public SafeToSpendResponse safeToSpend(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Monat im Format YYYY-MM, z. B. 2026-07. Weggelassen = "
                    + "laufender Monat.", example = "2026-07")
            @RequestParam(required = false) String month) {
        // month == null ist der Default-Fall und nicht dasselbe wie ein leerer Parameter:
        // MonthParser lehnt "" bewusst ab (?month= ist eine Angabe, nur keine brauchbare),
        // während ein fehlender Parameter den laufenden Monat meint.
        return month == null
                ? safeToSpendService.calculate(userId)
                : safeToSpendService.calculate(userId, MonthParser.parse(month));
    }
}
