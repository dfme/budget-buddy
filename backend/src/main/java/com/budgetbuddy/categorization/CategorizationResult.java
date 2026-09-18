package com.budgetbuddy.categorization;

/**
 * Ergebnis einer Kategorisierung (BE-PDF-06): die Kategorie plus die Stufe der Hybrid-Kette
 * (ADR-6), die sie geliefert hat.
 *
 * <p>Die {@link Source} entstand für die Instrumentierung des Import-Flows: Erst das
 * Lookup-/Claude-Verhältnis pro Import macht die ADR-6-Annahme von 70–80% Lookup-Trefferquote
 * überprüfbar.
 *
 * <p><strong>Seit BE-CAT-11 trägt sie zusätzlich, ob das Ergebnis belastbar ist.</strong> Die drei
 * Claude-Werte unterscheiden sich in zwei Dimensionen, und beide werden gebraucht:
 *
 * <table border="1">
 *   <caption>Die drei Claude-Werte</caption>
 *   <tr><th>Wert</th><th>Request hinaus?</th><th>Echtes Modell-Ergebnis?</th></tr>
 *   <tr><td>{@link Source#CLAUDE}</td><td>ja</td><td>ja</td></tr>
 *   <tr><td>{@link Source#CLAUDE_FALLBACK}</td><td>ja</td><td>nein</td></tr>
 *   <tr><td>{@link Source#CLAUDE_SKIPPED}</td><td>nein</td><td>nein</td></tr>
 * </table>
 *
 * <p>Für die <strong>ADR-6-Trefferquote</strong> zählen alle drei gleich: Der Lookup kannte den
 * Text nicht. Für die <strong>Laufzeit</strong> zählen die ersten beiden zusammen und der dritte
 * nicht — ein offener Circuit Breaker oder ein fehlender API-Key liefern {@code Sonstiges} ohne
 * HTTP-Request und damit ohne Latenz. Ohne diese Trennung (Review PR #174) läse sich «12 via
 * Claude» neben «Kategorisierung 180 ms» widersprüchlich, und #157 begründet die Instrumentierung
 * genau damit, dass der Unterschied zwischen 2 und 12 Claude-Calls grob der zwischen 1s und 25s
 * ist. Für den <strong>Lerneffekt</strong> (BE-CAT-11) zählt nur der erste.
 *
 * <p>Der Javadoc dieses Records behauptete bis BE-CAT-11, die {@code Source} transportiere
 * «Herkunft, keine Qualität», und {@code CLAUDE} schloss den Fallback eines fehlgeschlagenen Calls
 * ausdrücklich ein. Das stimmt nicht mehr: {@link Source#CLAUDE_FALLBACK} ist genau dieser Fall,
 * und er steht jetzt getrennt, weil der Lerneffekt ihn nicht in die Lookup-Tabelle schreiben darf.
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
         * Claude-API-Stufe (Stufe 2) hat die Transaktion <em>tatsächlich beantwortet</em>: Der
         * Request ging hinaus, die Antwort war lesbar, und sie enthielt einen gültigen Eintrag für
         * genau diese Transaktion.
         *
         * <p><strong>Der einzige Wert, aus dem gelernt wird</strong> (BE-CAT-11): Nur hier steht
         * ein Modell-Ergebnis hinter der Kategorie und kein Ersatz für ein ausgebliebenes.
         */
        CLAUDE,
        /**
         * Claude-Stufe erreicht, Request ging hinaus — aber für diese Transaktion kam nichts
         * Brauchbares zurück, und sie ist auf {@code Sonstiges} gefallen: fehlgeschlagener Call
         * (Timeout, IO, HTTP), unlesbare oder abgeschnittene Antwort, oder eine im Bündel
         * ausgelassene Nummer.
         *
         * <p><strong>Wird nie gelernt</strong> (BE-CAT-11): Sonst friert ein Netzwerkfehler einen
         * Händler dauerhaft auf {@code Sonstiges} ein, weil die Lookup-Stufe ihn künftig vor
         * Claude abfängt und er nie wieder neu bewertet wird. Für die Laufzeit zählt er wie
         * {@link #CLAUDE}: Der Request hat seine Zeit gekostet, auch ohne Ergebnis.
         */
        CLAUDE_FALLBACK,
        /**
         * Claude-Stufe erreicht, aber <em>ohne</em> HTTP-Request auf {@code Sonstiges} gefallen:
         * offener Circuit Breaker (BE-CAT-02), kein konfigurierter API-Key, oder das
         * überschrittene Zeitbudget des Import-Jobs (ADR-14). Zählt für die ADR-6-Trefferquote wie
         * {@link #CLAUDE}, kostet aber keine Latenz — und wird wie {@link #CLAUDE_FALLBACK} nie
         * gelernt.
         */
        CLAUDE_SKIPPED
    }

    public CategorizationResult {
        if (category == null || source == null) {
            throw new IllegalArgumentException("category und source dürfen nicht null sein");
        }
    }
}
