package com.budgetbuddy.transaction.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request-Body für {@code PUT /transactions/{id}/direction} (BE-PDF-10, US-04).
 *
 * <p>{@code income} ist die vom Nutzer entschiedene Richtung: {@code true} für eine Gutschrift,
 * {@code false} für eine Belastung.
 *
 * <p>Der Wrapper-Typ {@link Boolean} statt {@code boolean}, damit {@link NotNull} überhaupt etwas
 * zu prüfen hat: Ein primitives Feld ohne Wert im JSON käme als {@code false} an — also als
 * «Belastung», eine inhaltliche Aussage, die der Client nie gemacht hat. Genau die falsche Vorgabe
 * für einen Endpoint, dessen Zweck es ist, eine ungeprüfte Belastung zu bestätigen oder zu drehen.
 * So endet ein fehlendes Feld stattdessen in 400.
 */
public record UpdateDirectionRequest(@NotNull Boolean income) {
}
