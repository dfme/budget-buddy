package com.budgetbuddy.budget;

/**
 * Wird geworfen, wenn eine Fixkosten-Eingabe die fachlichen Regeln aus US-03 verletzt.
 *
 * <p>Trägt den Namen des verletzten Feldes mit, weil US-03 eine <em>feldspezifische</em>
 * Fehlermeldung verlangt: der Wizard markiert genau die Zeile, die der User korrigieren muss. Eine
 * Exception-Klasse pro Feld wäre dieselbe Information in drei Typen — der Feldname gehört in die
 * Nutzlast, nicht in den Klassennamen.
 *
 * <p>Die Meldung beschreibt die verletzte Regel und wiederholt den eingegebenen Wert nicht: sie
 * geht in eine HTTP-Antwort, und Eingaben ungeprüft zurückzuspiegeln ist der kurze Weg zu
 * Reflected-XSS im Client.
 */
public class InvalidFixedCostException extends RuntimeException {

    private final String field;

    /**
     * @param field Name des verletzten Feldes: {@code "bezeichnung"}, {@code "betrag"} oder
     *     {@code "intervall"} — oder {@code "request"}, wenn gar kein Body ankam und sich der
     *     Fehler keinem einzelnen Feld zuordnen lässt.
     * @param message Beschreibung der verletzten Regel, ohne den eingegebenen Wert.
     */
    public InvalidFixedCostException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** Name des verletzten Feldes — vom Controller (BE-FC-03) in die 400-Antwort abgebildet. */
    public String getField() {
        return field;
    }
}
