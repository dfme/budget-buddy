package com.budgetbuddy.categorization;

/**
 * Ergebnis einer Kategorisierung (BE-PDF-06): die Kategorie plus die Stufe der Hybrid-Kette
 * (ADR-6), die sie geliefert hat.
 *
 * <p>Die {@link Source} existiert für die Instrumentierung des Import-Flows: Erst das
 * Lookup-/Claude-Verhältnis pro Import macht die ADR-6-Annahme von 70–80% Lookup-Trefferquote
 * überprüfbar. Sie transportiert Herkunft, keine Qualität — auch ein {@code Sonstiges} aus dem
 * Claude-Fallback (Fehler, Circuit Breaker) zählt als {@link Source#CLAUDE}, weil es für die
 * Trefferquote nur darauf ankommt, dass der Lookup den Text <em>nicht</em> kannte.
 *
 * @param category die ermittelte Kategorie, nie {@code null}.
 * @param source die Stufe der Kette, die die Kategorie geliefert hat.
 */
public record CategorizationResult(Category category, Source source) {

    /** Stufe der Hybrid-Kette (ADR-6), die eine Transaktion kategorisiert hat. */
    public enum Source {
        /** Deterministischer Treffer in der {@code category_lookup}-Tabelle (Stufe 1). */
        LOOKUP,
        /** Claude-API-Stufe (Stufe 2) — inklusive deren Fallback auf {@code Sonstiges}. */
        CLAUDE
    }

    public CategorizationResult {
        if (category == null || source == null) {
            throw new IllegalArgumentException("category und source dürfen nicht null sein");
        }
    }
}
