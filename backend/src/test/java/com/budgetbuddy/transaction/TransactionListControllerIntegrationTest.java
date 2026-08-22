package com.budgetbuddy.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.support.PostgresTestDatabase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest von {@code GET /transactions} (FE-CAT-03, FE-CAT-05) gegen echtes PostgreSQL +
 * Flyway.
 *
 * <p>Seeding über das {@link TransactionRepository} und eigene Datenbank auf dem gemeinsamen
 * Testcontainer, analog {@code TransactionSummaryControllerIntegrationTest} (Begründung in
 * {@code PostgresTestDatabase}).
 *
 * <p>Zwei User werden angelegt, nicht einer: die Mandantentrennung ist bei einem Endpoint, der
 * Transaktions-IDs herausgibt, der Punkt, der wirklich schiefgehen kann — ein grüner Happy Path
 * belegt sie nicht.
 *
 * <p>Seit FE-CAT-05 laufen Kategorie-Filter, Reihenfolge und Seitengrenzen in der Query. Dieser
 * Test ist damit ihr einziger echter Nachweis — im Unit-Test steht davor ein Mock.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionListControllerIntegrationTest {

    /** Buchungen in einer eigenen Kategorie, um über die Seitengrenze hinauszukommen. */
    private static final int FREIZEIT_COUNT = 21;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "transaction_list");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    private long laraId;
    private long marcId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");
        laraId = createUser("lara@example.ch");
        marcId = createUser("marc@example.ch");

        // Laras Juli: zwei Ausgaben, eine davon noch nicht kategorisiert.
        save(laraId, "2026-07-03", "MIGROS BERN", "60.00", false, "Lebensmittel");
        save(laraId, "2026-07-20", "UNBEKANNT AG", "25.00", false, null);
        // Gutschrift im Juli → keine Ausgabe, darf nicht erscheinen.
        save(laraId, "2026-07-25", "LOHN ARBEITGEBER", "3000.00", true, "Einkommen");
        // Ausgabe im Juni → anderer Monat.
        save(laraId, "2026-06-15", "MIETE", "1200.00", false, "Wohnen");
        // Marcs Juli — dieselbe Periode, fremder User.
        save(marcId, "2026-07-10", "MARCS KAFFEE", "8.00", false, "Restaurant");
    }

    /**
     * 21 Buchungen in «Freizeit», alle am selben Tag und in aufsteigender Reihenfolge gespeichert.
     * Gleiches Datum mit Absicht: dann entscheidet allein die ID über die Reihenfolge, und die
     * Seitengrenze lässt sich ohne Datums-Arithmetik nachrechnen — die zuletzt gespeicherte
     * Buchung steht zuoberst auf Seite 0, die zuerst gespeicherte allein auf Seite 1.
     */
    private void seedFreizeit(long userId) {
        for (int i = 1; i <= FREIZEIT_COUNT; i++) {
            save(userId, "2026-07-15", String.format("FREIZEIT %02d", i), "10.00", false,
                    "Freizeit");
        }
    }

    private long createUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                email, "bcrypt-hash", new BigDecimal("4200.00"), true);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void save(long userId, String datum, String text, String betrag, boolean income,
            String category) {
        transactionRepository.save(new Transaction(
                userId, LocalDate.parse(datum), text, new BigDecimal(betrag), income, category,
                null));
    }

    private Cookie jwtCookie(long userId) {
        return new Cookie("jwt", jwtService.generateToken(userId));
    }

    @Test
    void returnsExpensesOfTheMonthNewestFirst() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-07").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(2))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("UNBEKANNT AG"))
                .andExpect(jsonPath("$.transactions[0].buchungsdatum").value("2026-07-20"))
                .andExpect(jsonPath("$.transactions[0].betrag").value(25.00))
                .andExpect(jsonPath("$.transactions[0].income").value(false))
                // Nicht kategorisiert → 'Sonstiges', damit das Dropdown eine Vorauswahl hat.
                .andExpect(jsonPath("$.transactions[0].category").value("Sonstiges"))
                .andExpect(jsonPath("$.transactions[0].id").isNumber())
                .andExpect(jsonPath("$.transactions[1].buchungstext").value("MIGROS BERN"))
                .andExpect(jsonPath("$.transactions[1].category").value("Lebensmittel"));
    }

    @Test
    void excludesIncomeAndOtherMonths() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-07").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(
                        "$.transactions[?(@.buchungstext == 'LOHN ARBEITGEBER')]").isEmpty())
                .andExpect(jsonPath("$.transactions[?(@.buchungstext == 'MIETE')]").isEmpty());
    }

    @Test
    void doesNotLeakTransactionsOfAnotherUser() throws Exception {
        // Marc fragt denselben Monat ab und sieht ausschliesslich seine eigene Buchung.
        mockMvc.perform(get("/api/transactions").param("month", "2026-07").cookie(jwtCookie(marcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("MARCS KAFFEE"))
                .andExpect(jsonPath(
                        "$.transactions[?(@.buchungstext == 'MIGROS BERN')]").isEmpty());
    }

    @Test
    void doesNotLeakTransactionsOfAnotherUserWhenPaging() throws Exception {
        // Der gefilterte Pfad ist eine eigene Query — die Mandantentrennung muss auch dort stehen,
        // und zwar auf jeder Seite, nicht nur auf der ersten.
        seedFreizeit(laraId);
        save(marcId, "2026-07-15", "MARCS FREIZEIT", "10.00", false, "Freizeit");

        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Freizeit").cookie(jwtCookie(marcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("MARCS FREIZEIT"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void filtersByCategory() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Lebensmittel").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("MIGROS BERN"));
    }

    @Test
    void filterOnSonstigesMatchesUncategorizedTransactions() throws Exception {
        // Das coalesce in der Query bildet dieselbe Regel ab wie labelOf() auf dem Antwortpfad.
        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Sonstiges").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("UNBEKANNT AG"));
    }

    @Test
    void filtersOnLabelsThatTheEnumDoesNotKnow() throws Exception {
        // Was die Übersicht anzeigt, muss sich auch aufklappen lassen — auch ein Label, das die
        // Category-Enum nicht kennt. Eine Validierung des Filters ergäbe hier eine 400.
        save(laraId, "2026-07-08", "NEUE KATEGORIE AG", "12.00", false, "Kryptowährung");

        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Kryptowährung").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("NEUE KATEGORIE AG"));
    }

    @Test
    void emptyMonthReturnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-01").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void invalidMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-13").cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unmatchedCategoryReturnsEmptyList() throws Exception {
        // Der Filter validiert das Vokabular bewusst nicht — sonst liessen sich genau die Zeilen
        // nicht aufklappen, die mit einem unerwarteten Label in der Übersicht stehen.
        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Lebensmitel").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(0));
    }

    @Test
    void withoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-07"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTwentyEntriesWithoutPaginationParameters() throws Exception {
        // AC 3: ein Aufruf ohne Begrenzung liefert eine definierte Menge, keinen stillen Vollload.
        seedFreizeit(laraId);

        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Freizeit").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(20))
                .andExpect(jsonPath("$.hasMore").value(true));
    }

    @Test
    void secondPageContinuesExactlyWhereTheFirstEnded() throws Exception {
        seedFreizeit(laraId);

        // Alle 21 am selben Tag, aufsteigend gespeichert → Reihenfolge ist ID absteigend, also
        // FREIZEIT 21 … FREIZEIT 02 auf Seite 0 und FREIZEIT 01 allein auf Seite 1. Damit ist
        // belegt, dass die Seitengrenze weder etwas überspringt noch etwas doppelt zeigt.
        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Freizeit").param("page", "0").param("size", "20")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("FREIZEIT 21"))
                .andExpect(jsonPath("$.transactions[19].buchungstext").value("FREIZEIT 02"))
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Freizeit").param("page", "1").param("size", "20")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(1))
                .andExpect(jsonPath("$.transactions[0].buchungstext").value("FREIZEIT 01"))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void paginatesTheUnfilteredListAsWell() throws Exception {
        // Ungefilterter und gefilterter Pfad sind zwei verschiedene Queries — beide müssen die
        // Begrenzung tragen. Laras Juli: 21 Freizeit-Buchungen plus die zwei aus dem Seed.
        seedFreizeit(laraId);

        mockMvc.perform(get("/api/transactions").param("month", "2026-07").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(20))
                .andExpect(jsonPath("$.hasMore").value(true));

        mockMvc.perform(get("/api/transactions").param("month", "2026-07").param("page", "1")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(3))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void refetchesTheWholeLoadedWindowInOneRequest() throws Exception {
        // Das ist der Request, den das Frontend nach einer Kategorie-Korrektur absetzt: Seite 0
        // mit der Grösse des bereits geladenen Fensters. Ohne ihn fiele die Liste auf 20 zurück.
        seedFreizeit(laraId);

        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Freizeit").param("page", "0").param("size", "40")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(FREIZEIT_COUNT))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void pageBeyondTheEndIsEmptyInsteadOfAnError() throws Exception {
        seedFreizeit(laraId);

        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("category", "Freizeit").param("page", "9")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactions.length()").value(0))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void negativePageReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-07").param("page", "-1")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sizeAboveTheMaximumReturns400() throws Exception {
        // Ohne diese Grenze liesse sich der Vollload, den US-13 ausschliesst, per size wiederholen.
        mockMvc.perform(get("/api/transactions").param("month", "2026-07")
                        .param("size", String.valueOf(TransactionListService.MAX_PAGE_SIZE + 1))
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listsTheMonthsWithExpensesNewestFirst() throws Exception {
        // Laras Juli hat zwei Ausgaben — sie erscheinen als ein Monat, nicht als zwei. Der Juni
        // kommt aus der MIETE-Buchung.
        mockMvc.perform(get("/api/transactions/months").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("2026-07"))
                .andExpect(jsonPath("$[1]").value("2026-06"));
    }

    @Test
    void monthsSpanYearsAndPadSingleDigitMonths() throws Exception {
        // Der eigentliche Grund für diesen Endpoint: alte Kontoauszüge. Eine im Frontend
        // festgelegte Jahresspanne träfe genau diese Monate nicht.
        save(laraId, "2019-08-04", "ALTER AUSZUG", "42.00", false, "Sonstiges");

        mockMvc.perform(get("/api/transactions/months").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[2]").value("2019-08"));
    }

    @Test
    void monthsWithOnlyIncomeDoNotAppear() throws Exception {
        // Ein Monat mit ausschliesslich Gutschriften hat in der Kategorie-Übersicht nichts
        // anzuzeigen — er gehört nicht ins Dropdown.
        save(laraId, "2026-05-25", "LOHN ARBEITGEBER", "3000.00", true, "Einkommen");

        mockMvc.perform(get("/api/transactions/months").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@ == '2026-05')]").isEmpty());
    }

    @Test
    void doesNotLeakTheMonthsOfAnotherUser() throws Exception {
        // Der Endpoint gibt preis, *wann* jemand Geld ausgegeben hat — für sich schon eine
        // Aussage über eine Person, auch ohne Beträge.
        save(marcId, "2020-01-09", "MARCS ALTE BUCHUNG", "15.00", false, "Shopping");

        mockMvc.perform(get("/api/transactions/months").cookie(jwtCookie(laraId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@ == '2020-01')]").isEmpty());

        mockMvc.perform(get("/api/transactions/months").cookie(jwtCookie(marcId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0]").value("2026-07"))
                .andExpect(jsonPath("$[1]").value("2020-01"));
    }

    @Test
    void userWithoutExpensesGetsAnEmptyMonthList() throws Exception {
        long neu = createUser("neu@example.ch");

        mockMvc.perform(get("/api/transactions/months").cookie(jwtCookie(neu)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void monthsWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/transactions/months"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sizeBelowOneReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions").param("month", "2026-07").param("size", "0")
                        .cookie(jwtCookie(laraId)))
                .andExpect(status().isBadRequest());
    }
}
