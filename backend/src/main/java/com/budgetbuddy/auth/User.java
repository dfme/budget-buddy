package com.budgetbuddy.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA-Entity der {@code users}-Tabelle (Flyway V01, DB-01).
 *
 * <p>{@code monthlyIncome} ist nullable (Onboarding noch nicht abgeschlossen) und immer
 * {@link BigDecimal} — niemals {@code double}/{@code float} (ADR-9).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "monthly_income")
    private BigDecimal monthlyIncome;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    protected User() {
        // JPA
    }

    /**
     * Erzeugt einen neuen User bei der Registrierung (BE-AUTH-03).
     *
     * @param email E-Mail-Adresse (eindeutig).
     * @param passwordHash bcrypt-Hash des Passworts — niemals Klartext (ADR-7).
     */
    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.onboardingCompleted = false;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public BigDecimal getMonthlyIncome() {
        return monthlyIncome;
    }

    public void setMonthlyIncome(BigDecimal monthlyIncome) {
        this.monthlyIncome = monthlyIncome;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }

    /**
     * Markiert das Onboarding als abgeschlossen (US-03, BE-FC-03).
     *
     * <p>Bewusst eine Domänenmethode statt eines {@code setOnboardingCompleted(boolean)}: das Flag
     * kennt genau einen Übergang, {@code false → true}. Ein Setter böte auch den Rückweg an, den
     * die Domäne nicht vorsieht — der Wizard wird nicht wieder «unabgeschlossen».
     *
     * <p>Mehrfachaufruf ist unschädlich: der Endpoint ist damit idempotent und braucht keine
     * Sonderbehandlung für den zweiten Klick.
     */
    public void completeOnboarding() {
        this.onboardingCompleted = true;
    }
}
