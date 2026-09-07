package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest von {@code GET /transactions/uncertain} und
 * {@code PUT /transactions/{id}/direction} (BE-PDF-10, US-04) gegen echtes PostgreSQL + Flyway.
 *
 * <p>Deckt AC 1 (Markierung ist persistiert und lesbar) und AC 2 (die Richtung lässt sich
 * korrigieren) ab, dazu die Mandantentrennung auf beiden Endpoints.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link TransactionCategoryControllerIntegrationTest} (Begründung in
 * {@code PostgresTestDatabase}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TransactionDirectionControllerIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "transaction_direction");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TransactionRepository transactionRepository;

    private long userId;
    private long otherUserId;

    /** Unsicher markierte Belastung im Juli 2026 — der zu prüfende Fall. */
    private long uncertainId;

    /** Gesicherte Belastung im selben Monat — darf nie in der Prüfliste auftauchen. */
    private long certainId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");

        userId = insertUser("lara@example.ch");
        otherUserId = insertUser("marc@example.ch");

        uncertainId = save(userId, LocalDate.of(2026, 7, 3), "GIRO POST", true);
        certainId = save(userId, LocalDate.of(2026, 7, 4), "MIGROS MMM BERN", false);
    }

    private long save(long owner, LocalDate date, String text, boolean directionUncertain) {
        return transactionRepository.save(new Transaction(owner, date, text, null,
                new BigDecimal("120.00"), false, directionUncertain, "Sonstiges", null)).getId();
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                email, "bcrypt-hash", new BigDecimal("4200.00"), true);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Cookie jwtCookie(long uid) {
        return new Cookie("jwt", jwtService.generateToken(uid));
    }

    private String body(String income) {
        return "{\"income\":" + income + "}";
    }

    @Test
    void uncertainListContainsOnlyTheMarkedBookingsOfTheMonth() throws Exception {
        // AC 1: Die Markierung überlebt die Persistenz und ist über die API lesbar. Die gesicherte
        // Buchung daneben belegt, dass nicht einfach alles ausgeliefert wird.
        mockMvc.perform(get("/api/transactions/uncertain").param("month", "2026-07")
                        .cookie(jwtCookie(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(uncertainId))
                .andExpect(jsonPath("$[0].buchungstext").value("GIRO POST"))
                .andExpect(jsonPath("$[0].directionUncertain").value(true))
                .andExpect(jsonPath("$[0].income").value(false));
    }

    @Test
    void uncertainListIsScopedToTheAuthenticatedUser() throws Exception {
        // Mandantentrennung: Marc sieht Laras offenen Fall nicht — auch nicht, dass es ihn gibt.
        mockMvc.perform(get("/api/transactions/uncertain").param("month", "2026-07")
                        .cookie(jwtCookie(otherUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void uncertainListIsScopedToTheRequestedMonth() throws Exception {
        mockMvc.perform(get("/api/transactions/uncertain").param("month", "2026-06")
                        .cookie(jwtCookie(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void malformedMonthReturns400() throws Exception {
        mockMvc.perform(get("/api/transactions/uncertain").param("month", "Juli")
                        .cookie(jwtCookie(userId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void correctingToIncomeFlipsTheDirectionAndClearsTheFlag() throws Exception {
        // AC 2: Die Richtung lässt sich korrigieren. Danach ist die Buchung eine Gutschrift und
        // steht nicht mehr in der Prüfliste.
        mockMvc.perform(put("/api/transactions/" + uncertainId + "/direction")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("true")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(uncertainId))
                .andExpect(jsonPath("$.income").value(true))
                .andExpect(jsonPath("$.directionUncertain").value(false));

        assertThat(transactionRepository.findById(uncertainId))
                .get()
                .satisfies(tx -> {
                    assertThat(tx.isIncome()).isTrue();
                    assertThat(tx.isDirectionUncertain()).isFalse();
                });

        mockMvc.perform(get("/api/transactions/uncertain").param("month", "2026-07")
                        .cookie(jwtCookie(userId)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void confirmingTheAssumedDirectionAlsoClearsTheFlag() throws Exception {
        // Bestätigen ist eine Entscheidung: Die Buchung bleibt Belastung, aber sie ist es ab jetzt
        // nachweislich. Ohne dieses Verhalten stünde sie für immer in der Prüfliste, und der
        // Nutzer hätte keine Möglichkeit, sie loszuwerden.
        mockMvc.perform(put("/api/transactions/" + uncertainId + "/direction")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.income").value(false))
                .andExpect(jsonPath("$.directionUncertain").value(false));

        mockMvc.perform(get("/api/transactions/uncertain").param("month", "2026-07")
                        .cookie(jwtCookie(userId)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void foreignTransactionReturns404AndIsLeftUntouched() throws Exception {
        // Mandantentrennung auf dem Schreibpfad: Marc darf Laras Buchung nicht drehen. 404 ohne
        // Auskunft darüber, ob die ID existiert.
        mockMvc.perform(put("/api/transactions/" + uncertainId + "/direction")
                        .cookie(jwtCookie(otherUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("true")))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.findById(uncertainId))
                .get()
                .satisfies(tx -> {
                    assertThat(tx.isIncome()).isFalse();
                    assertThat(tx.isDirectionUncertain()).isTrue();
                });
    }

    @Test
    void missingIncomeFieldReturns400() throws Exception {
        // Der Wrapper-Typ Boolean sorgt dafür, dass ein fehlendes Feld nicht still als "Belastung"
        // durchgeht — eine inhaltliche Aussage, die der Client nie gemacht hat.
        mockMvc.perform(put("/api/transactions/" + uncertainId + "/direction")
                        .cookie(jwtCookie(userId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/transactions/uncertain").param("month", "2026-07"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/transactions/" + certainId + "/direction")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("true")))
                .andExpect(status().isUnauthorized());
    }
}
