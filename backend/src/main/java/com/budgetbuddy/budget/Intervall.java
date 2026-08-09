package com.budgetbuddy.budget;

/**
 * Zahlungsintervall einer Fixkosten-Position (US-03: {@code monatlich}, {@code quartalsweise},
 * {@code jaehrlich}).
 *
 * <p>Das {@code label} ist der Wert, der in der Spalte {@code fixed_costs.intervall} persistiert
 * wird — bewusst ASCII und kleingeschrieben, wie im Kommentar der Migration V03 festgehalten. Der
 * Anzeigetext «jährlich» mit Umlaut ist Sache des Frontends; ein Umlaut im DB-Wert und im späteren
 * API-Contract würde Encoding-Fallen über Datenbank, JSON und E2E-Assertions hinweg eröffnen.
 *
 * <p>Die Umrechnung auf einen Monatsbetrag (÷ 1, ÷ 3, ÷ 12) steht bewusst <em>nicht</em> hier,
 * sondern im {@code FixedCostService} (BE-FC-02) — sie ist Fachlogik der Safe-to-Spend-Rechnung,
 * nicht Teil der Persistenz-Abbildung.
 *
 * @see IntervallConverter
 */
public enum Intervall {
    MONATLICH("monatlich"),
    QUARTALSWEISE("quartalsweise"),
    JAEHRLICH("jaehrlich");

    private final String label;

    Intervall(String label) {
        this.label = label;
    }

    /** Persistierter Wert der Spalte {@code fixed_costs.intervall} (z. B. {@code "quartalsweise"}). */
    public String getLabel() {
        return label;
    }

    /**
     * Bildet einen DB-Wert aus {@code fixed_costs.intervall} auf die Enum-Konstante ab.
     *
     * @param label Intervall-Wert, exakt wie in der DB gespeichert.
     * @return das passende {@link Intervall}.
     * @throws IllegalArgumentException wenn kein Label passt — signalisiert einen inkonsistenten
     *     Datenbestand und darf nicht stillschweigend zu {@code null} werden.
     */
    public static Intervall fromLabel(String label) {
        for (Intervall intervall : values()) {
            if (intervall.label.equals(label)) {
                return intervall;
            }
        }
        throw new IllegalArgumentException("Unbekanntes Intervall in der Datenbank: " + label);
    }
}
