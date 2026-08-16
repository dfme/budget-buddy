package com.budgetbuddy.categorization;

/**
 * Ergebnis einer Kategorisierung (BE-PDF-06): die Kategorie plus die Stufe der Hybrid-Kette
 * (ADR-6), die sie geliefert hat.
 *
 * <p>Die {@link Source} existiert für die Instrumentierung des Import-Flows: Erst das
 * Lookup-/Claude-Verhältnis pro Import macht die ADR-6-Annahme von 70–80% Lookup-Trefferquote
 * überprüfbar. Sie transportiert Herkunft, keine Qualität — auch ein {@code Sonstiges} aus einem
 * fehlgeschlagenen Claude-Call zählt als {@link Source#CLAUDE}, weil es für die Trefferquote nur
 * darauf ankommt, dass der Lookup den Text <em>nicht</em> kannte.
 *
 * <p><strong>{@link Source#CLAUDE_SKIPPED} ist von {@link Source#CLAUDE} getrennt</strong> (Review
 * PR #174), weil die beiden Zahlen zwei verschiedene Fragen beantworten. Für die ADR-6-Trefferquote
 * zählen sie gleich (der Lookup kannte den Text nicht); für die Laufzeit nicht: ein offener Circuit
 * Breaker oder ein fehlender API-Key liefern {@code Sonstiges} <em>ohne</em> HTTP-Request und damit
 * ohne Latenz. Ohne die Trennung läse sich «12 via Claude» neben «Kategorisierung 180 ms»
 * widersprüchlich — und #157 begründet die Instrumentierung genau damit, dass der Unterschied
 * zwischen 2 und 12 Claude-Calls grob der zwischen 1s und 25s ist.
 *
 * @param category die ermittelte Kategorie, nie {@code null}.
 * @param source die Stufe der Kette, die die Kategorie geliefert hat.
 */
public record CategorizationResult(Category category, Source source) {

    /** Stufe der Hybrid-Kette (ADR-6), die eine Transaktion kategorisiert hat. */
    public enum Source {
        /** Deterministischer Treffer in der {@code category_lookup}-Tabelle (Stufe 1). */
        LOOKUP,
        /**
         * Claude-API-Stufe (Stufe 2), Request ging hinaus — inklusive Fallback auf
         * {@code Sonstiges} nach einem fehlgeschlagenen oder unbrauchbar beantworteten Call.
         */
        CLAUDE,
        /**
         * Claude-Stufe erreicht, aber <em>ohne</em> HTTP-Request auf {@code Sonstiges} gefallen:
         * offener Circuit Breaker (BE-CAT-02) oder kein konfigurierter API-Key. Zählt für die
         * ADR-6-Trefferquote wie {@link #CLAUDE}, kostet aber keine Latenz.
         */
        CLAUDE_SKIPPED
    }

    public CategorizationResult {
        if (category == null || source == null) {
            throw new IllegalArgumentException("category und source dürfen nicht null sein");
        }
    }
}
