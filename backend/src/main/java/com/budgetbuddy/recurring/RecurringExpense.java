package com.budgetbuddy.recurring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/**
 * JPA-Entity der {@code recurring_expenses}-Tabelle (Flyway V11, DB-09).
 *
 * <p>Eine erkannte wiederkehrende Ausgabe eines Users (US-08): ein Empfänger, der in mindestens
 * zwei aufeinanderfolgenden Monaten einen ähnlichen Betrag belastet hat. Pro User und
 * {@code payeeKey} gibt es genau eine Zeile ({@code UNIQUE (user_id, payee_key)}) — so bleibt ein
 * «Kein Abo» ({@link RecurringExpenseStatus#DISMISSED}) dauerhaft, weil die Erkennung daneben keine
 * zweite Zeile anlegen kann.
 *
 * <p>{@code payeeKey} steht ausschliesslich in Grossschreibung — der Vertrag aus V11, den der
 * {@link RecurringExpenseService} beim Schreiben durchsetzt.
 *
 * <p>{@code firstDetectedMonth} ist der <em>erste Monat der Abo-Reihe in den Daten</em>, nicht der
 * Monat des Erkennungslaufs: Letzteren hält {@code createdAt} bereits fest. Gespeichert als
 * {@code YYYY-MM}-Text, wie die Spalte es vorsieht; {@link YearMonth#toString()} und
 * {@link YearMonth#parse(CharSequence)} sind für dieses Format zueinander invers.
 *
 * <p>{@code amount} ist {@link BigDecimal} (ADR-9) — nie {@code double}/{@code float}.
 */
@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "payee_key", nullable = false)
    private String payeeKey;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringExpenseStatus status;

    @Column(name = "first_detected_month", nullable = false)
    private String firstDetectedMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecurringExpense() {
        // JPA
    }

    /**
     * Legt einen neu erkannten Eintrag mit Status {@link RecurringExpenseStatus#DETECTED} an.
     *
     * @param userId ID des besitzenden Users.
     * @param payeeKey normalisierter Empfänger in Grossschreibung.
     * @param amount Betrag der jüngsten erkannten Belastung, Skala 2.
     * @param firstDetectedMonth erster Monat der Abo-Reihe in den Daten.
     * @param createdAt Zeitpunkt des Erkennungslaufs aus der injizierten {@code Clock}.
     */
    public RecurringExpense(Long userId, String payeeKey, BigDecimal amount,
            YearMonth firstDetectedMonth, Instant createdAt) {
        this.userId = userId;
        this.payeeKey = payeeKey;
        this.amount = amount;
        this.status = RecurringExpenseStatus.DETECTED;
        this.firstDetectedMonth = firstDetectedMonth.toString();
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPayeeKey() {
        return payeeKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public RecurringExpenseStatus getStatus() {
        return status;
    }

    public YearMonth getFirstDetectedMonth() {
        return YearMonth.parse(firstDetectedMonth);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
