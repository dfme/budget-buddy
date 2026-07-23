package com.budgetbuddy.transaction;

/**
 * Der im Request übergebene Kategorie-Wert ist nicht leer, entspricht aber keinem gültigen
 * Kategorie-Label (siehe {@link com.budgetbuddy.categorization.Category}). Wird vom
 * {@link TransactionExceptionHandler} auf HTTP 400 abgebildet.
 *
 * <p>Ein leerer/fehlender Wert wird bereits durch die Bean-Validation ({@code @NotBlank}) als 400
 * abgefangen und erreicht diese Exception nicht.
 */
public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(String category) {
        super("Ungültige Kategorie: " + category);
    }
}
