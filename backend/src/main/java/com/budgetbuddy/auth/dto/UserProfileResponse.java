package com.budgetbuddy.auth.dto;

import com.budgetbuddy.auth.User;
import java.math.BigDecimal;

/**
 * Profil-Antwort für {@code GET /users/me} sowie Register/Login (BE-AUTH-03).
 *
 * <p>{@code monthlyIncome} kann {@code null} sein, solange das Onboarding nicht abgeschlossen ist.
 */
public record UserProfileResponse(
        Long id, String email, BigDecimal monthlyIncome, boolean onboardingCompleted) {

    /** Mappt eine {@link User}-Entity auf die Profil-Antwort. */
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getMonthlyIncome(),
                user.isOnboardingCompleted());
    }
}
