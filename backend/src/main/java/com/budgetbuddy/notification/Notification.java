package com.budgetbuddy.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA-Entity der {@code notifications}-Tabelle (Flyway V10, DB-08).
 *
 * <p>Eine In-App-Benachrichtigung eines Users (Fundament für US-08). {@code type} grenzt die
 * auslösende Quelle ab (z. B. {@code RECURRING_EXPENSE_DETECTED}) und bleibt bewusst ein freier
 * String statt eines Java-Enums — kein Aufrufer dieses Ports existiert bislang, ein Enum jetzt
 * hiesse, Werte für einen noch nicht gebauten Consumer zu raten. {@code referenceId} ist ein
 * polymorpher, FK-loser Verweis auf die auslösende Zeile (siehe V10-Migration).
 *
 * <p>{@code readAt} trägt sowohl den Gelesen-Status als auch den Zeitpunkt: {@code null} bedeutet
 * ungelesen. {@link #markRead(Instant)} ist idempotent und überschreibt einen bereits gesetzten
 * Zeitpunkt nicht.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String type;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(nullable = false)
    private String message;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
        // JPA
    }

    /**
     * Legt eine neue, ungelesene Benachrichtigung an.
     *
     * @param userId ID des besitzenden Users.
     * @param type Quelle der Benachrichtigung, z. B. {@code "RECURRING_EXPENSE_DETECTED"}.
     * @param referenceId optionaler Verweis auf die auslösende Zeile eines anderen Moduls.
     * @param message Anzeigetext.
     * @param createdAt Anlagezeitpunkt aus der injizierten {@code Clock}.
     */
    public Notification(Long userId, String type, Long referenceId, String message, Instant createdAt) {
        this.userId = userId;
        this.type = type;
        this.referenceId = referenceId;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getType() {
        return type;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public String getMessage() {
        return message;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /**
     * Markiert die Benachrichtigung als gelesen.
     *
     * <p>Idempotent: ein zweiter Aufruf überschreibt den bereits gesetzten Zeitpunkt nicht — er
     * bleibt der Zeitpunkt des <em>ersten</em> Lesens.
     */
    public void markRead(Instant readAt) {
        if (this.readAt == null) {
            this.readAt = readAt;
        }
    }
}
