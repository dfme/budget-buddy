package com.budgetbuddy.transaction.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request-Body für {@code PUT /transactions/{id}/category}.
 *
 * <p>{@code category} ist das deutsche Kategorie-Label (z. B. {@code "Lebensmittel"}), konsistent
 * zur Summary-API. {@link NotBlank} fängt leere/fehlende Werte als 400 ab; ein nicht-leerer, aber
 * ungültiger Wert wird im Service gegen die feste Kategorienliste geprüft.
 */
public record UpdateCategoryRequest(@NotBlank String category) {
}
