package com.budgetbuddy.budget;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.budget.dto.FixedCostRequest;
import com.budgetbuddy.support.PostgresTestDatabase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest von {@code GET /budget/safe-to-spend} (BE-STS-03) gegen echtes PostgreSQL +
 * Flyway.
 *
 * <p>Die Rechenregeln selbst sind in {@code SafeToSpendServiceTest} und
 * {@link SafeToSpendServiceIntegrationTest} abgedeckt; hier geht es um die HTTP-Kante: Statuscode,
 * Wire-Format, Authentifizierung und Mandantentrennung von aussen.
 *
 * <p>Das <strong>Wire-Format</strong> ist kein Selbstzweck. FE-STS-01/02/03 lesen genau diese fünf
 * Felder, und zwei Details brechen den Contract, ohne dass ein Frontend-Test es merkt — dessen Spec
 * mockt die Antwort selbst: ein {@code NON_NULL}-Include liesse {@code amount} im
 * {@code noIncome}-Fall ganz verschwinden statt es als {@code null} zu senden, und ein umbenanntes
 * {@code negative} wäre im Client still {@code undefined} (also falsy — das Warn-Banner bliebe aus).
 *
 * <p>Die {@link Clock} ist als {@link MockitoBean} auf einen festen Zeitpunkt gestellt: sonst hinge
 * {@code weeksLeft} am Kalendertag des CI-Laufs und der Test wäre an den meisten Tagen grün und an
 * den übrigen rot.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link FixedCostControllerIntegrationTest} (Begründung in {@code PostgresTestDatabase}). Test-User
 * und Transaktionen werden per {@link JdbcTemplate} eingefügt: ein Zugriff über
 * {@code UserRepository} oder {@code TransactionRepository} wäre genau der modulübergreifende
 * Zugriff, den CLAUDE.md untersagt.
 *
 * <p>Die OpenAPI-Prüfung (AC4) sitzt bewusst in dieser Klasse und nicht in einer eigenen wie
 * {@link FixedCostOpenApiTest}: für drei Assertions über einen einzigen Endpoint einen zweiten
 * Spring-Context hochzufahren kostet CI-Zeit ohne Erkenntnisgewinn. {@code /v3/api-docs} ist in
 * {@code SecurityConfig.PUBLIC_PATHS} und aus demselben MockMvc ohne JWT erreichbar.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class BudgetControllerIntegrationTest {

    /** 11.08.2026, 12:00 Ortszeit — im August verbleiben ab hier 21 Tage, also 3 Wochen. */
    private static final Instant STICHTAG = LocalDate.of(2026, 8, 11)
            .atTime(12, 0)
            .atZone(ZoneId.of("Europe/Zurich"))
            .toInstant();

    private static final String PFAD = "/budget/safe-to-spend";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "budget_controller");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FixedCostService fixedCostService;

    @MockitoBean private Clock clock;

    @BeforeEach
    void resetStateAndFixTheClock() {
        when(clock.instant()).thenReturn(STICHTAG);
        jdbcTemplate.update("DELETE FROM transactions");
        jdbcTemplate.update("DELETE FROM fixed_costs");
        jdbcTemplate.update("DELETE FROM users");
    }

    // --- AC1/AC2/AC3: die Antwort selbst ---

    @Test
    void returnsAllFiveFieldsWithTheCalculatedAmount() throws Exception {
        long lara = insertUser("lara@example.ch", new BigDecimal("3000.00"));
        fixedCostService.create(lara,
                new FixedCostRequest("Miete", new BigDecimal("1200.00"), "monatlich"));
        fixedCostService.create(lara,
                new FixedCostRequest("Versicherung", new BigDecimal("1200.00"), "jaehrlich"));
        insertExpense(lara, LocalDate.of(2026, 8, 3), "COOP BERN", new BigDecimal("150.00"));
        insertExpense(lara, LocalDate.of(2026, 8, 9), "SBB", new BigDecimal("50.00"));

        // Fixkosten monatlich: 1200.00 + (1200.00 ÷ 12) = 1300.00
        // (3000.00 − 1300.00 − 200.00) ÷ 3 = 500.00
        mockMvc.perform(get(PFAD).cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.00))
                .andExpect(jsonPath("$.weeksLeft").value(3))
                .andExpect(jsonPath("$.negative").value(false))
                .andExpect(jsonPath("$.noIncome").value(false))
                // Bei erfasstem Einkommen bleibt der Vorschlag leer — aber als null im Body, nicht
                // als fehlender Schlüssel (Begründung bei amountIsPresentAsNullWhenNoIncomeIsSet).
                .andExpect(content().string(containsString("\"incomeSuggestion\":null")));
    }

    /**
     * AC3 im Grenzfall: am letzten Tag des Monats bleibt genau ein Tag, der Divisor ist trotzdem 1
     * und nicht 0. Ein Divisor 0 wäre kein falscher Betrag, sondern eine 500er-Antwort.
     */
    @Test
    void weeksLeftIsOneOnTheLastDayOfTheMonth() throws Exception {
        when(clock.instant()).thenReturn(LocalDate.of(2026, 8, 31)
                .atTime(12, 0)
                .atZone(ZoneId.of("Europe/Zurich"))
                .toInstant());
        long lara = insertUser("lara-letzter-tag@example.ch", new BigDecimal("700.00"));

        mockMvc.perform(get(PFAD).cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weeksLeft").value(1))
                .andExpect(jsonPath("$.amount").value(700.00));
    }

    @Test
    void reportsNegativeWhenTheMonthIsAlreadyOverspent() throws Exception {
        long marc = insertUser("marc@example.ch", new BigDecimal("2000.00"));
        fixedCostService.create(marc,
                new FixedCostRequest("Miete", new BigDecimal("1500.00"), "monatlich"));
        insertExpense(marc, LocalDate.of(2026, 8, 2), "SHOPPING", new BigDecimal("800.00"));

        // (2000.00 − 1500.00 − 800.00) ÷ 3 = −100.00
        mockMvc.perform(get(PFAD).cookie(jwtCookie(marc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(-100.00))
                .andExpect(jsonPath("$.negative").value(true))
                .andExpect(jsonPath("$.noIncome").value(false));
    }

    @Test
    void reportsNoIncomeWithASuggestionFromRecurringCredits() throws Exception {
        long marc = insertUser("marc-ohne-einkommen@example.ch", null);
        insertIncome(marc, LocalDate.of(2026, 6, 25), "Saläreingang", new BigDecimal("6800.00"));
        insertIncome(marc, LocalDate.of(2026, 7, 25), "Saläreingang", new BigDecimal("6800.00"));

        mockMvc.perform(get(PFAD).cookie(jwtCookie(marc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noIncome").value(true))
                .andExpect(jsonPath("$.negative").value(false))
                .andExpect(jsonPath("$.weeksLeft").value(3))
                .andExpect(jsonPath("$.incomeSuggestion").value(6800.00));
    }

    // --- Wire-Format: der Contract, den FE-STS-01/02/03 konsumieren ---

    /**
     * {@code amount} muss im {@code noIncome}-Fall als {@code null} im Body stehen und nicht fehlen.
     * Ein globales {@code JsonInclude.NON_NULL} liesse den Schlüssel verschwinden; der Client
     * könnte «kein Einkommen erfasst» dann nicht mehr von einem unvollständigen Response
     * unterscheiden.
     *
     * <p>Geprüft wird der Rohbody, nicht per {@code jsonPath}: für JsonPath ist ein Feld mit dem
     * Wert {@code null} von einem fehlenden Feld nicht zu unterscheiden — beide liefern
     * {@code null} und erfüllen sogar {@code doesNotExist()}. Genau diesen Unterschied hält der
     * Test fest.
     */
    @Test
    void amountIsPresentAsNullWhenNoIncomeIsSet() throws Exception {
        long marc = insertUser("marc-null-amount@example.ch", null);

        mockMvc.perform(get(PFAD).cookie(jwtCookie(marc)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"amount\":null")))
                .andExpect(content().string(containsString("\"incomeSuggestion\":null")));
    }

    /**
     * Der Feldname aus der Contract-Anpassung an #23: {@code negative}, nicht {@code isNegative}.
     * Ein umbenanntes Feld wäre im Client still {@code undefined} und damit falsy — das Warn-Banner
     * aus US-06 bliebe bei überzogenem Budget einfach aus, ohne Fehlermeldung.
     */
    @Test
    void negativeFlagIsNamedWithoutTheIsPrefix() throws Exception {
        long marc = insertUser("marc-feldname@example.ch", new BigDecimal("100.00"));
        insertExpense(marc, LocalDate.of(2026, 8, 2), "SHOPPING", new BigDecimal("500.00"));

        mockMvc.perform(get(PFAD).cookie(jwtCookie(marc)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"negative\":true")))
                .andExpect(content().string(not(containsString("isNegative"))));
    }

    /** Eine Jackson-Property könnte {@link BigDecimal} auf String umstellen — das bräche FE-STS-01. */
    @Test
    void amountsAreSerializedAsJsonNumbersNotAsStrings() throws Exception {
        long marc = insertUser("marc-zahlen@example.ch", null);
        insertIncome(marc, LocalDate.of(2026, 6, 25), "Saläreingang", new BigDecimal("6800.00"));
        insertIncome(marc, LocalDate.of(2026, 7, 25), "Saläreingang", new BigDecimal("6800.00"));

        mockMvc.perform(get(PFAD).cookie(jwtCookie(marc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeSuggestion").isNumber())
                .andExpect(jsonPath("$.weeksLeft").isNumber());

        long lara = insertUser("lara-zahlen@example.ch", new BigDecimal("3000.00"));
        mockMvc.perform(get(PFAD).cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").isNumber());
    }

    // --- Mandantentrennung ---

    @Test
    void aForeignUsersDataDoesNotAffectTheAnswer() throws Exception {
        long lara = insertUser("lara-isolation@example.ch", new BigDecimal("3000.00"));
        fixedCostService.create(lara,
                new FixedCostRequest("Miete", new BigDecimal("1200.00"), "monatlich"));
        insertExpense(lara, LocalDate.of(2026, 8, 3), "COOP", new BigDecimal("300.00"));

        // Marc bekommt im selben Monat deutlich höhere Fixkosten und Ausgaben. Würde eine der
        // Abfragen den User nicht einschränken, sänke Laras Betrag messbar.
        long marc = insertUser("marc-isolation@example.ch", new BigDecimal("9000.00"));
        fixedCostService.create(marc,
                new FixedCostRequest("Loft", new BigDecimal("4000.00"), "monatlich"));
        insertExpense(marc, LocalDate.of(2026, 8, 3), "DIGITEC", new BigDecimal("2500.00"));
        insertExpense(marc, LocalDate.of(2026, 8, 7), "RESTAURANT", new BigDecimal("400.00"));

        // (3000.00 − 1200.00 − 300.00) ÷ 3 = 500.00
        mockMvc.perform(get(PFAD).cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(500.00));

        // Gegenrichtung: (9000.00 − 4000.00 − 2900.00) ÷ 3 = 700.00
        mockMvc.perform(get(PFAD).cookie(jwtCookie(marc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(700.00));
    }

    // --- Authentifizierung ---

    @Test
    void withoutJwtTheEndpointReturns401() throws Exception {
        mockMvc.perform(get(PFAD)).andExpect(status().isUnauthorized());
    }

    @Test
    void withAnInvalidJwtTheEndpointReturns401() throws Exception {
        mockMvc.perform(get(PFAD).cookie(new Cookie("jwt", "nicht.signiert.hier")))
                .andExpect(status().isUnauthorized());
    }

    // --- AC4: Swagger UI ---

    /**
     * Geprüft wird das generierte OpenAPI-Dokument unter {@code /v3/api-docs}, nicht die
     * Swagger-UI-Seite: die UI rendert genau dieses Dokument, und ein Blick ins UI ist kein
     * automatisierter Nachweis. Der Response-Media-Type ist {@code *}{@code /*}, weil die Controller
     * dieses Projekts kein {@code produces} deklarieren — dasselbe Verhalten wie bei
     * {@code GET /fixed-costs} (siehe {@link FixedCostOpenApiTest}).
     */
    @Test
    void theEndpointAppearsInTheOpenApiDocumentWithItsResponseSchema() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['" + PFAD + "'].get").exists())
                .andExpect(jsonPath("$.paths['" + PFAD + "'].get.summary").isNotEmpty())
                .andExpect(jsonPath("$.paths['" + PFAD + "'].get.responses['200'].content"
                        + "['*/*'].schema.$ref")
                        .value("#/components/schemas/SafeToSpendResponse"));
    }

    /** Alle fünf Felder aus AC1 müssen auch im dokumentierten Schema stehen, nicht nur im Body. */
    @Test
    void theResponseSchemaDescribesAllFiveFields() throws Exception {
        String schema = "$.components.schemas.SafeToSpendResponse.properties.";

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath(schema + "amount.type").value("number"))
                .andExpect(jsonPath(schema + "weeksLeft.type").value("integer"))
                .andExpect(jsonPath(schema + "negative.type").value("boolean"))
                .andExpect(jsonPath(schema + "noIncome.type").value("boolean"))
                .andExpect(jsonPath(schema + "incomeSuggestion.type").value("number"));
    }

    // --- Helfer ---

    private long insertUser(String email, BigDecimal monthlyIncome) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income) VALUES (?, ?, ?)",
                email, "$2a$10$test.only.not.a.real.hash", monthlyIncome);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertExpense(long userId, LocalDate datum, String text, BigDecimal betrag) {
        insertTransaction(userId, datum, text, betrag, false);
    }

    private void insertIncome(long userId, LocalDate datum, String text, BigDecimal betrag) {
        insertTransaction(userId, datum, text, betrag, true);
    }

    private void insertTransaction(
            long userId, LocalDate datum, String text, BigDecimal betrag, boolean income) {
        jdbcTemplate.update(
                "INSERT INTO transactions (user_id, buchungsdatum, buchungstext, betrag, is_income) "
                        + "VALUES (?, ?, ?, ?, ?)",
                userId, datum, text, betrag, income);
    }

    private Cookie jwtCookie(long uid) {
        return new Cookie("jwt", jwtService.generateToken(uid));
    }
}
