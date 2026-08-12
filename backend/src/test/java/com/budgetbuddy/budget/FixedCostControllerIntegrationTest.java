package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.support.PostgresTestDatabase;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest der Fixkosten-Endpoints (BE-FC-03) gegen echtes PostgreSQL + Flyway.
 *
 * <p>Deckt die vier Acceptance Criteria von #12 ab: Statuscodes, Mandantentrennung, 401 ohne JWT
 * und das Wire-Format. Letzteres ist kein Selbstzweck — {@code fixed-cost.model.ts} aus FE-FC-01
 * erwartet {@code intervall} als ASCII-Label und {@code betrag} als JSON-Zahl. Jackson serialisiert
 * ein Enum per Default als Konstantennamen ({@code "MONATLICH"}), und eine Jackson-Property könnte
 * {@code BigDecimal} auf String umstellen; beides bräche den Contract, ohne dass ein Frontend-Test
 * es merkt — dessen Spec mockt die Antwort selbst.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@code TransactionCategoryControllerIntegrationTest} (Begründung in {@code PostgresTestDatabase}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FixedCostControllerIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "fixed_cost_controller");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private FixedCostRepository fixedCostRepository;

    private long lara;
    private long marc;

    @BeforeEach
    void seed() {
        fixedCostRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");

        lara = insertUser("lara@example.ch", new BigDecimal("4200.00"));
        marc = insertUser("marc@example.ch", new BigDecimal("4200.00"));
    }

    // --- AC1: Statuscodes der CRUD-Endpoints ---

    @Test
    void postCreatesTheEntryAndAnswersWith201() throws Exception {
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "1200.00", "monatlich")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.bezeichnung").value("Miete"))
                .andExpect(jsonPath("$.monatsbetrag").value(1200.00));

        assertThat(fixedCostRepository.findByUserIdOrderByIdAsc(lara))
                .extracting(FixedCost::getBezeichnung)
                .containsExactly("Miete");
    }

    @Test
    void getReturnsTheSingleEntryWith200() throws Exception {
        long id = createEntry(lara, "Miete", "1200.00", "monatlich");

        mockMvc.perform(get("/fixed-costs/" + id).cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.bezeichnung").value("Miete"));
    }

    @Test
    void putUpdatesTheEntryWith200() throws Exception {
        long id = createEntry(lara, "Miete", "1200.00", "monatlich");

        mockMvc.perform(put("/fixed-costs/" + id)
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Serafe", "335.00", "jaehrlich")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bezeichnung").value("Serafe"))
                .andExpect(jsonPath("$.intervall").value("jaehrlich"))
                // 335.00 / 12 = 27.9166… → 27.92
                .andExpect(jsonPath("$.monatsbetrag").value(27.92));
    }

    @Test
    void deleteRemovesTheEntryAndAnswersWith204AndNoBody() throws Exception {
        long id = createEntry(lara, "Miete", "1200.00", "monatlich");

        mockMvc.perform(delete("/fixed-costs/" + id).cookie(jwtCookie(lara)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(fixedCostRepository.findByIdAndUserId(id, lara)).isEmpty();
    }

    @Test
    void listReturnsSumAndWarningFlag() throws Exception {
        createEntry(lara, "Miete", "1200.00", "monatlich");
        createEntry(lara, "Handy", "100.00", "quartalsweise");

        mockMvc.perform(get("/fixed-costs").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixedCosts.length()").value(2))
                // 1200.00 + 33.33 — die Summe der bereits gerundeten Zeilen
                .andExpect(jsonPath("$.summeMonatlich").value(1233.33))
                .andExpect(jsonPath("$.monthlyIncome").value(4200.00))
                .andExpect(jsonPath("$.exceedsIncome").value(false));
    }

    @Test
    void listWarnsWhenFixedCostsReachTheIncome() throws Exception {
        createEntry(lara, "Miete", "4200.00", "monatlich");

        mockMvc.perform(get("/fixed-costs").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exceedsIncome").value(true));
    }

    // --- Wire-Format: der Contract, den FE-FC-01 bereits konsumiert ---

    @Test
    void intervallIsSerializedAsAsciiLabelNotAsEnumName() throws Exception {
        long id = createEntry(lara, "Serafe", "335.00", "jaehrlich");

        // Ohne String-Feld im DTO stünde hier "JAEHRLICH" — Jacksons Default für ein Enum.
        mockMvc.perform(get("/fixed-costs/" + id).cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervall").value("jaehrlich"));
    }

    @Test
    void betragIsSerializedAsJsonNumberNotAsString() throws Exception {
        long id = createEntry(lara, "Handy", "39.90", "monatlich");

        mockMvc.perform(get("/fixed-costs/" + id).cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.betrag").isNumber())
                .andExpect(jsonPath("$.monatsbetrag").isNumber());
    }

    @Test
    void requestAcceptsIntervallAsAsciiLabel() throws Exception {
        // Gegenrichtung: der Wizard sendet das Label, nicht den Enum-Namen.
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Handy", "100.00", "quartalsweise")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.intervall").value("quartalsweise"));
    }

    @Test
    void enumConstantNameIsRejectedAsIntervall() throws Exception {
        // Hält die Richtung des Contracts fest: ASCII-Label ja, Konstantenname nein.
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Handy", "100.00", "MONATLICH")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("intervall"));
    }

    // --- AC4: Mandantentrennung ---

    @Test
    void aForeignEntryIsNotReadableUpdatableOrDeletable() throws Exception {
        long id = createEntry(lara, "Miete", "1200.00", "monatlich");

        mockMvc.perform(get("/fixed-costs/" + id).cookie(jwtCookie(marc)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/fixed-costs/" + id)
                        .cookie(jwtCookie(marc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Gekapert", "1.00", "monatlich")))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/fixed-costs/" + id).cookie(jwtCookie(marc)))
                .andExpect(status().isNotFound());

        // Ein 404 allein beweist nicht, dass nichts geschrieben wurde.
        assertThat(fixedCostRepository.findByIdAndUserId(id, lara))
                .get()
                .extracting(FixedCost::getBezeichnung)
                .isEqualTo("Miete");
    }

    @Test
    void listShowsOnlyTheOwnEntries() throws Exception {
        createEntry(lara, "Miete", "1200.00", "monatlich");
        createEntry(marc, "Fitness", "89.00", "monatlich");

        mockMvc.perform(get("/fixed-costs").cookie(jwtCookie(marc)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixedCosts.length()").value(1))
                .andExpect(jsonPath("$.fixedCosts[0].bezeichnung").value("Fitness"));
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(get("/fixed-costs/999999").cookie(jwtCookie(lara)))
                .andExpect(status().isNotFound());
    }

    @Test
    void notFoundCarriesNoBody() throws Exception {
        // Bewusst body-los: jede Zusatzauskunft verriete, ob eine fremde ID existiert.
        mockMvc.perform(delete("/fixed-costs/999999").cookie(jwtCookie(lara)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    // --- AC4: 401 ohne JWT, auf allen Pfaden ---

    @Test
    void withoutJwtEveryEndpointReturns401() throws Exception {
        mockMvc.perform(get("/fixed-costs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/fixed-costs/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/fixed-costs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "1200.00", "monatlich")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/fixed-costs/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "1200.00", "monatlich")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/fixed-costs/1")).andExpect(status().isUnauthorized());
    }

    // --- 400 mit Feldname (US-03: feldspezifische Meldung) ---

    @Test
    void blankBezeichnungReturns400WithFieldName() throws Exception {
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("   ", "1200.00", "monatlich")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("bezeichnung"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void zeroBetragReturns400WithFieldName() throws Exception {
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "0.00", "monatlich")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("betrag"));
    }

    @Test
    void betragWithThreeDecimalsReturns400WithFieldName() throws Exception {
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "12.345", "monatlich")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("betrag"));
    }

    @Test
    void errorMessageDoesNotEchoTheSubmittedValue() throws Exception {
        // Der Payload muss die *Bezeichnungs*-Regel verletzen, sonst prüft der Test nichts: bei
        // gültiger Bezeichnung plus ungültigem Betrag antwortet validate() mit der Betrag-Meldung,
        // die den Payload nie in der Hand hatte. Deshalb 145 Zeichen (Grenze ist 100) bei gültigem
        // Betrag — jetzt hängt die Assertion an der Meldung, die den Wert wirklich gesehen hat.
        String payload = "<script>alert(1)</script>" + "A".repeat(120);

        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(payload, "1200.00", "monatlich")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("bezeichnung"))
                .andExpect(content().string(not(containsString("<script>"))))
                .andExpect(content().string(not(containsString("AAAA"))));
    }

    @Test
    void intervallErrorMessageDoesNotEchoTheSubmittedValue() throws Exception {
        // Gegenstück für das dritte Feld: auch der Intervall-Wert kommt aus der Fremdeingabe.
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "1200.00", "<script>alert(1)</script>")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("intervall"))
                .andExpect(content().string(not(containsString("<script>"))));
    }

    // --- 400 aus dem Body-Parsing: entsteht vor dem Controller, trägt trotzdem field ---

    @Test
    void commaAmountReturns400WithBetragAsField() throws Exception {
        // Der praktische Fall: "12,50" ist in einem Schweizer Wizard eine naheliegende Eingabe und
        // kommt als String an. Jackson bricht ab, bevor der Controller läuft — ohne eigenen Handler
        // gäbe es hier einen 400 ohne field, obwohl OpenAPI den Body zusagt.
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"Miete\",\"betrag\":\"12,50\","
                                + "\"intervall\":\"monatlich\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("betrag"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                // Die Eingabe selbst darf auch hier nicht zurückgespiegelt werden.
                .andExpect(content().string(not(containsString("12,50"))));
    }

    @Test
    void missingBodyReturns400WithRequestAsField() throws Exception {
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("request"));
    }

    @Test
    void malformedJsonReturns400WithRequestAsField() throws Exception {
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("request"));
    }

    @Test
    void everyBadRequestCarriesAFieldRegardlessOfWhereItWasDetected() throws Exception {
        // Der eigentliche Contract-Punkt: #26 liest err.error.field und darf nie undefined sehen.
        // Fachliche Validierung (Service) und Parsing-Fehler (Jackson) laufen über verschiedene
        // Pfade und müssen denselben Body liefern.
        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "0.00", "monatlich")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").isNotEmpty());

        mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"Miete\",\"betrag\":\"abc\","
                                + "\"intervall\":\"monatlich\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").isNotEmpty());
    }

    @Test
    void malformedBodyOnUpdateAlsoCarriesAField() throws Exception {
        long id = createEntry(lara, "Miete", "1200.00", "monatlich");

        mockMvc.perform(put("/fixed-costs/" + id)
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bezeichnung\":\"Miete\",\"betrag\":\"12,50\","
                                + "\"intervall\":\"monatlich\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("betrag"));
    }

    @Test
    void invalidUpdateReturns400WithFieldName() throws Exception {
        long id = createEntry(lara, "Miete", "1200.00", "monatlich");

        mockMvc.perform(put("/fixed-costs/" + id)
                        .cookie(jwtCookie(lara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Miete", "-1.00", "monatlich")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("betrag"));
    }

    // --- Helfer ---

    private long insertUser(String email, BigDecimal monthlyIncome) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                email, "bcrypt-hash", monthlyIncome, false);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Cookie jwtCookie(long uid) {
        return new Cookie("jwt", jwtService.generateToken(uid));
    }

    private static String body(String bezeichnung, String betrag, String intervall) {
        return "{\"bezeichnung\":\"" + bezeichnung + "\",\"betrag\":" + betrag
                + ",\"intervall\":\"" + intervall + "\"}";
    }

    /** Legt eine Position über den Service-Pfad an und liefert ihre ID. */
    private long createEntry(long userId, String bezeichnung, String betrag, String intervall)
            throws Exception {
        String response = mockMvc.perform(post("/fixed-costs")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(bezeichnung, betrag, intervall)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.parse(response).read("$.id", Integer.class).longValue();
    }
}
