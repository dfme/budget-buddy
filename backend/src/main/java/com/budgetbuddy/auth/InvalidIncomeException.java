package com.budgetbuddy.auth;

/**
 * Wird geworfen, wenn das übergebene Monatseinkommen die fachlichen Regeln verletzt (BE-AUTH-08).
 *
 * <p>Trägt den Namen des verletzten Feldes mit, obwohl {@code PUT /api/users/me/income} nur ein
 * einziges Feld hat: Der Body ist damit formgleich zu {@code FixedCostErrorResponse}, und ein
 * Client, der beide Endpoints bedient, braucht keine zweite Fehlerbehandlung. Mit US-14 kommt das
 * Einkommen ausserdem in ein Formular neben andere Felder — dann trägt {@code field} echte
 * Information.
 *
 * <p>Die Meldung beschreibt die verletzte Regel und wiederholt den eingegebenen Wert <strong>nicht
 * </strong>: sie geht in eine HTTP-Antwort, und Eingaben ungeprüft zurückzuspiegeln ist der kurze
 * Weg zu Reflected-XSS im Client (gleiche Regel wie bei {@code InvalidFixedCostException}).
 *
 * <p>Bewusst ein eigener Typ und nicht {@code InvalidFixedCostException}: Der läge im
 * {@code budget}-Modul, und ein Zugriff darauf wäre genau die modulübergreifende Abhängigkeit, die
 * CLAUDE.md untersagt. {@code FixedCost}, {@code PdfImport} und {@code Transaction} halten aus
 * demselben Grund je einen eigenen.
 */
public class InvalidIncomeException extends RuntimeException {

    private final String field;

    /**
     * @param field Name des verletzten Feldes — {@code "betrag"}, oder {@code "request"}, wenn sich
     *     der Fehler keinem einzelnen Feld zuordnen lässt (kein Body, abgeschnittenes JSON).
     * @param message Beschreibung der verletzten Regel, ohne den eingegebenen Wert.
     */
    public InvalidIncomeException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Name des verletzten Feldes — vom Advice in die 400-Antwort abgebildet. */
    public String getField() {
        return field;
    }
}
