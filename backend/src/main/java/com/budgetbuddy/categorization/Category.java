package com.budgetbuddy.categorization;

/**
 * Fixe Kategorienliste der Hybrid-Kategorisierung (ADR-6, siehe CLAUDE.md).
 *
 * <p>Das {@code label} ist der deutsche Anzeigetext und zugleich der Wert, der in der Spalte
 * {@code category_lookup.category} sowie später in {@code transactions.category} persistiert wird.
 * {@link #fromLabel(String)} bildet einen solchen DB-String zurück auf die Enum-Konstante.
 *
 * <p>{@link #SONSTIGES} ist die Fallback-Kategorie, wenn weder Lookup noch Claude eine Kategorie
 * liefern. Sie steht bewusst als letzte Konstante: BE-CAT-10 hat die Liste von 13 auf 17
 * Kategorien erweitert und die vier neuen davor eingefügt, damit der Fallback das Ende der Liste
 * bleibt und die Reihenfolge der bestehenden 13 sich nicht verschiebt. Die Reihenfolge ist reine
 * Anzeige — persistiert wird das {@code label}, nie der Ordinalwert.
 */
public enum Category {
    WOHNEN("Wohnen"),
    LEBENSMITTEL("Lebensmittel"),
    TRANSPORT("Transport"),
    VERSICHERUNG("Versicherung"),
    TELEKOM("Telekom"),
    GESUNDHEIT("Gesundheit"),
    FREIZEIT("Freizeit"),
    RESTAURANT("Restaurant"),
    SHOPPING("Shopping"),
    BILDUNG("Bildung"),
    EINKOMMEN("Einkommen"),
    SPAREN("Sparen"),
    PERSOENLICHES("Persönliches"),
    STEUERN("Steuern"),
    BARGELDBEZUG("Bargeldbezug"),
    REISEN("Reisen"),
    SONSTIGES("Sonstiges");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    /** Deutscher Anzeigetext, wie er in der DB gespeichert wird (z. B. {@code "Lebensmittel"}). */
    public String getLabel() {
        return label;
    }

    /**
     * Bildet einen DB-Kategorie-String (z. B. aus {@code category_lookup.category}) auf die
     * Enum-Konstante ab.
     *
     * @param label deutscher Kategorietext, exakt wie in der DB gespeichert.
     * @return die passende {@link Category}.
     * @throws IllegalArgumentException wenn kein Label passt — signalisiert inkonsistente Seed-Daten
     *     und darf nicht stillschweigend geschluckt werden.
     */
    public static Category fromLabel(String label) {
        for (Category category : values()) {
            if (category.label.equals(label)) {
                return category;
            }
        }
        throw new IllegalArgumentException("Unbekannte Kategorie in der Datenbank: " + label);
    }
}
