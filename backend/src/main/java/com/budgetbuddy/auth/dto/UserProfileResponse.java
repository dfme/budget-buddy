package com.budgetbuddy.auth.dto;

import java.math.BigDecimal;

/**
 * Profil-Antwort für {@code GET /users/me}.
 *
 * <p>{@code monthlyIncome} kann {@code null} sein, solange das Onboarding nicht abgeschlossen ist.
 */
public record UserProfileResponse(
        Long id, String email, BigDecimal monthlyIncome, boolean onboardingCompleted) {
}
