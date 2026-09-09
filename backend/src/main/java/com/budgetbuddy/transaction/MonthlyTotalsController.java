package com.budgetbuddy.transaction;

import com.budgetbuddy.transaction.dto.MonthlyTotals;
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
 * Monats-Kennzahlen der Transaktionen: Einnahmen, Ausgaben und deren Differenz je Monat
 * (BE-STS-07, US-12).
 *
 * <p>Geschützt durch {@code anyRequest().authenticated()} (SecurityConfig); die User-ID kommt als
 * Principal aus dem {@code JwtCookieAuthenticationFilter}. Ohne gültiges JWT antwortet Spring
 * Security mit 401, bevor der Controller erreicht wird. Der Controller reicht die authentifizierte
 * User-ID durch und trifft keine eigene Entscheidung darüber, wer was sehen darf; die
 * Mandantentrennung liegt im {@link MonthlyTotalsService}, dessen beide Queries user-gebunden sind.
 *
 * <p>Eigener Controller neben {@link TransactionSummaryController} statt eines weiteren Endpoints
 * dort: das Projekt trennt die Transaktions-Endpoints nach Anliegen
 * ({@link TransactionListController}, {@link TransactionSummaryController},
 * {@link TransactionCategoryController}, {@link TransactionDirectionController}).
 *
 * <p>Der {@code month}-Parameter wird über den {@link MonthParser} gelesen, den auch
 * {@code GET /api/transactions}, {@code GET /api/transactions/summary} und
 * {@code GET /api/budget/safe-to-spend} verwenden — zwei Parser hiessen, dass zwei Endpoints
 * desselben Frontends dieselbe Zeichenkette unterschiedlich auslegen könnten. Die daraus
 * entstehende {@link InvalidMonthException} bildet der {@link TransactionExceptionHandler} auf 400
 * ab; dass dieser Controller dort eingetragen sein <em>muss</em>, ist im Javadoc des Parsers
 * begründet — ein {@code @RestControllerAdvice} ohne {@code assignableTypes} gibt es in diesem
 * Projekt bewusst nicht.
 */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "Auswertungen über die Transaktionen des eingeloggten Users")
public class MonthlyTotalsController {

    private final MonthlyTotalsService monthlyTotalsService;

    public MonthlyTotalsController(MonthlyTotalsService monthlyTotalsService) {
        this.monthlyTotalsService = monthlyTotalsService;
    }

    @GetMapping("/monthly-totals")
    @Operation(summary = "Einnahmen, Ausgaben und Differenz je Monat",
            description = "Liefert für ein Monatsfenster je Monat die Summe der Gutschriften "
                    + "(income), die Summe der Belastungen (expenses) und deren Differenz "
                    + "(difference = income − expenses). month ist der jüngste Monat des Fensters, "
                    + "months seine Grösse; die Zeilen kommen **neuester Monat zuerst**, analog zu "
                    + "GET /api/transactions/months.\n\n"
                    + "Alle Beträge sind CHF mit zwei Nachkommastellen. difference wird "
                    + "serverseitig gerechnet und mitgeliefert, damit der Client keine zwei "
                    + "JSON-Zahlen subtrahieren muss (ADR-9).\n\n"
                    + "Für **jeden** Monat des Fensters kommt eine Zeile, auch für einen ohne "
                    + "Buchungen. Ein Monat ohne jede Buchung trägt income, expenses und "
                    + "difference als null statt 0.00 — null ist damit von 'im Monat kam und ging "
                    + "exakt nichts, aber es gibt Buchungen' unterscheidbar. Ein Monat mit "
                    + "ausschliesslich Gutschriften trägt entsprechend expenses = 0.00.\n\n"
                    + "**expenses ist betragsgleich mit dem totalAmount von "
                    + "GET /api/transactions/summary** desselben Monats: nur Belastungen, ganzer "
                    + "Monat, kein Abzug der per Dauerauftrag bezahlten Fixkosten. Der "
                    + "Fixkosten-Abzug aus ADR-13 gehört allein in den Safe-to-Spend-Summanden.\n\n"
                    + "Buchungen, deren Richtung der PDF-Parser nur angenommen hat (BE-PDF-10), "
                    + "zählen wie überall sonst als Belastung — eine darunter liegende Gutschrift "
                    + "drückt also income und hebt expenses.\n\n"
                    + "Ein Monat in der **Zukunft** ist hier kein Fehler, anders als bei "
                    + "GET /api/budget/safe-to-spend: dort hat weeksLeft für einen künftigen Monat "
                    + "keine Bedeutung, hier gibt es einfach keine Buchungen. Die Zeile kommt mit "
                    + "nulls zurück.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Kennzahlen des Fensters zurückgegeben"),
        @ApiResponse(responseCode = "400",
                description = "month fehlt oder ist kein YYYY-MM, oder months liegt nicht zwischen "
                        + "1 und 12",
                content = {}),
        @ApiResponse(responseCode = "401", description = "Nicht authentifiziert", content = {})
    })
    public List<MonthlyTotals> monthlyTotals(
            @AuthenticationPrincipal Long userId,
            @Parameter(description = "Jüngster Monat des Fensters im Format YYYY-MM, z. B. 2026-07",
                    example = "2026-07")
            @RequestParam String month,
            // Der Default steht als Zahl im Service und wird hier nur in die für Annotationen
            // nötige Zeichenkette gehoben (konstanter Ausdruck) — so gibt es ihn nicht zweimal.
            @Parameter(description = "Grösse des Fensters in Monaten, 1 bis 12. Weggelassen = 3 "
                    + "(die drei Monate der Dashboard-Übersicht).")
            @RequestParam(defaultValue = "" + MonthlyTotalsService.DEFAULT_WINDOW_MONTHS)
            int months) {
        return monthlyTotalsService.totals(userId, MonthParser.parse(month), months);
    }
}
