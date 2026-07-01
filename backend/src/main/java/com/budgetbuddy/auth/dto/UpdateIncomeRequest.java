package com.budgetbuddy.auth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * Request-Body für {@code PUT /users/me/income}.
 *
 * <p>{@code betrag} muss vorhanden und strikt positiv sein ({@code > 0}). {@link Positive} arbeitet
 * exakt auf {@link BigDecimal} (ADR-9 — kein {@code double}/{@code float}).
 */
public record UpdateIncomeRequest(@NotNull @Positive BigDecimal betrag) {
}
