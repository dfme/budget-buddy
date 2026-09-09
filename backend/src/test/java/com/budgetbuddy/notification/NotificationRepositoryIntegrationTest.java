package com.budgetbuddy.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest von {@link Notification} und {@link NotificationRepository} gegen echtes
 * PostgreSQL + Flyway (BE-NOTIF-01). Belegt das Spalten-Mapping der V10-Migration, die
 * Sortierung "ungelesen zuerst" und die Mandantentrennung — letztere mit Gegenprobe aus Sicht
 * eines fremden Users, weil ein grüner Happy Path sie nicht beweist.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@code FixedCostRepositoryIntegrationTest}.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationRepositoryIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "notification_repository");
    }

    @Autowired private NotificationRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void entityIsPersistedIntoTheCorrectColumns() {
        Long userId = insertUser("mapping@example.com");
        Instant createdAt = Instant.parse("2026-09-01T08:00:00Z");

        Notification saved = repository.save(
                new Notification(userId, "RECURRING_EXPENSE_DETECTED", 55L, "Netflix erkannt",
                        createdAt));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, type, reference_id, message, read_at, created_at "
                        + "FROM notifications WHERE id = ?",
                saved.getId());

        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId);
        assertThat(row.get("type")).isEqualTo("RECURRING_EXPENSE_DETECTED");
        assertThat(((Number) row.get("reference_id")).longValue()).isEqualTo(55L);
        assertThat(row.get("message")).isEqualTo("Netflix erkannt");
        assertThat(row.get("read_at")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void referenceIdMayBeNull() {
        Long userId = insertUser("no-reference@example.com");

        Notification saved = repository.save(
                new Notification(userId, "MONTHLY_REPORT_READY", null, "Bericht bereit",
                        Instant.now()));

        assertThat(repository.findByIdAndUserId(saved.getId(), userId))
                .get()
                .extracting(Notification::getReferenceId)
                .isNull();
    }

    // --- Sortierung: ungelesen zuerst, dann neueste zuerst ---

    @Test
    void unreadNotificationsComeBeforeReadOnesRegardlessOfAge() {
        Long userId = insertUser("sort-unread@example.com");
        Instant now = Instant.now();

        Notification oldButUnread = repository.save(
                new Notification(userId, "A", null, "alt, ungelesen", now.minusSeconds(3600)));
        Notification newButRead = repository.save(
                new Notification(userId, "B", null, "neu, gelesen", now));
        newButRead.markRead(now);
        repository.save(newButRead);

        List<Notification> ordered = repository.findByUserIdOrderByUnreadFirstThenNewest(userId);

        assertThat(ordered).extracting(Notification::getId)
                .containsExactly(oldButUnread.getId(), newButRead.getId());
    }

    @Test
    void withinTheSameReadStatusNewestComesFirst() {
        Long userId = insertUser("sort-newest@example.com");
        Instant now = Instant.now();

        Notification older = repository.save(
                new Notification(userId, "A", null, "älter", now.minusSeconds(120)));
        Notification newer = repository.save(
                new Notification(userId, "B", null, "neuer", now));

        List<Notification> ordered = repository.findByUserIdOrderByUnreadFirstThenNewest(userId);

        assertThat(ordered).extracting(Notification::getId)
                .containsExactly(newer.getId(), older.getId());
    }

    // --- Mandantentrennung ---

    @Test
    void findByUserIdOnlyReturnsOwnEntries() {
        Long lara = insertUser("lara-list@example.com");
        Long marc = insertUser("marc-list@example.com");
        repository.save(new Notification(lara, "A", null, "Laras Meldung", Instant.now()));
        repository.save(new Notification(marc, "A", null, "Marcs Meldung", Instant.now()));

        List<Notification> laraEntries = repository.findByUserIdOrderByUnreadFirstThenNewest(lara);

        assertThat(laraEntries).extracting(Notification::getMessage)
                .containsExactly("Laras Meldung");
    }

    @Test
    void findByIdAndUserIdDoesNotLeakAForeignEntry() {
        Long lara = insertUser("lara-read@example.com");
        Long marc = insertUser("marc-read@example.com");
        Notification larasEntry = repository.save(
                new Notification(lara, "A", null, "Laras Meldung", Instant.now()));

        assertThat(repository.findByIdAndUserId(larasEntry.getId(), lara)).isPresent();
        assertThat(repository.findByIdAndUserId(larasEntry.getId(), marc)).isEmpty();
    }

    // deleteAllByUserId (@Modifying, ohne eigenes @Transactional — Begründung in
    // NotificationRepository) braucht eine Transaktion des Aufrufers und lässt sich deshalb nicht
    // direkt aus einem Testmethoden-Body aufrufen (gleiche Einschränkung wie bei
    // FixedCostRepository#deleteAllByUserId). Abgedeckt über NotificationCleanupServiceTest
    // (Mock-Ebene) und UserDeletionIntegrationTest (echte DB, innerhalb von
    // UserService.deleteUser's @Transactional).

    /**
     * Legt einen User direkt per SQL an — der FK {@code notifications.user_id} braucht eine echte
     * Zeile in {@code users}.
     */
    private Long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)",
                email, "$2a$10$test.only.not.a.real.hash");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
