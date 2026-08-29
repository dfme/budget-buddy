package com.budgetbuddy.auth.dto;

import com.budgetbuddy.auth.User;
import java.math.BigDecimal;

/**
 * Profil-Antwort für {@code GET /users/me} sowie Register/Login (BE-AUTH-03).
 *
 * <p>{@code monthlyIncome} kann {@code null} sein, solange das Onboarding nicht abgeschlossen ist.
 * {@code firstName}/{@code lastName} können {@code null} sein, solange kein Name hinterlegt ist
 * (BE-AUTH-05, #114) — Clients fallen dann auf die E-Mail-Darstellung zurück.
 */
public record UserProfileResponse(
        Long id,
        String email,
        BigDecimal monthlyIncome,
        boolean onboardingCompleted,
        String firstName,
        String lastName) {

    /** Mappt eine {@link User}-Entity auf die Profil-Antwort. */
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getMonthlyIncome(),
                user.isOnboardingCompleted(),
                user.getFirstName(),
                user.getLastName());
    }
}
