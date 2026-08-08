package com.budgetbuddy.budget;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA-Entity der {@code fixed_costs}-Tabelle (Flyway V03, DB-03).
 *
 * <p>Eine Fixkosten-Position eines Users — Miete, Krankenkasse, Handy-Abo (US-03). {@code betrag}
 * ist der Betrag <em>pro Intervall</em> und immer {@link BigDecimal}, niemals {@code double}/
 * {@code float} (ADR-9). Die Umrechnung auf einen Monatsbetrag ({@code quartalsweise} ÷ 3,
 * {@code jaehrlich} ÷ 12) passiert im {@code FixedCostService} (BE-FC-02), nicht hier.
 *
 * <p>Fachliche Validierung — nicht-leere Bezeichnung, {@code betrag > 0} — gehört ebenfalls in den
 * Service, konsistent zu {@link com.budgetbuddy.auth.User} und
 * {@link com.budgetbuddy.transaction.Transaction}, die im Entity ebenfalls keine Regeln tragen.
 *
 * <p>{@code userId} ist nach dem Anlegen unveränderlich: ein Eintrag darf den Besitzer nicht
 * wechseln. Der user-gebundene Zugriff ist im {@link FixedCostRepository} verankert.
 */
@Entity
@Table(name = "fixed_costs")
public class FixedCost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private String bezeichnung;

    @Column(nullable = false)
    private BigDecimal betrag;

    @Convert(converter = IntervallConverter.class)
    @Column(nullable = false)
    private Intervall intervall;

    protected FixedCost() {
        // JPA
    }

    /**
     * Erzeugt eine zu persistierende Fixkosten-Position (schreibender Pfad, BE-FC-02).
     *
     * @param userId ID des besitzenden Users.
     * @param bezeichnung Anzeigename der Position, z. B. {@code "Miete"}.
     * @param betrag Betrag in CHF pro {@code intervall} ({@link BigDecimal}, ADR-9).
     * @param intervall Zahlungsintervall.
     */
    public FixedCost(Long userId, String bezeichnung, BigDecimal betrag, Intervall intervall) {
        this.userId = userId;
        this.bezeichnung = bezeichnung;
        this.betrag = betrag;
        this.intervall = intervall;
    }

    public Long getId() {
        return id;
    }

    /** ID des besitzenden Users — nach dem Anlegen unveränderlich (kein Setter, bewusst). */
    public Long getUserId() {
        return userId;
    }

    public String getBezeichnung() {
        return bezeichnung;
    }

    public void setBezeichnung(String bezeichnung) {
        this.bezeichnung = bezeichnung;
    }

    /** Betrag in CHF pro {@link #getIntervall()} — nicht der normalisierte Monatsbetrag. */
    public BigDecimal getBetrag() {
        return betrag;
    }

    public void setBetrag(BigDecimal betrag) {
        this.betrag = betrag;
    }

    public Intervall getIntervall() {
        return intervall;
    }

    public void setIntervall(Intervall intervall) {
        this.intervall = intervall;
    }
}
