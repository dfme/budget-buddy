package com.budgetbuddy.categorization;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Preis eines Modells pro einer Million Tokens, in USD (BE-CAT-09).
 *
 * <p><strong>Warum das konfiguriert und nicht berechnet wird:</strong> Die Message-Response der
 * Claude-API enthält <em>keinen</em> Geldbetrag — {@code Usage} führt ausschliesslich Token-Zähler.
 * Abgerechnete Beträge liefert nur die Admin-API ({@code GET /v1/organizations/cost_report}),
 * tagesaggregiert, organisationsweit und gegen einen Admin-Key, den diese App nicht hat. Für eine
 * Zahl neben dem Import, zu dem sie gehört, bleibt deshalb nur die eigene Schätzung.
 *
 * <p>Der Preis ist damit eine <strong>Fremdannahme mit Verfallsdatum</strong>: Der Token-Wert
 * stimmt in fünf Jahren noch, der Preis ist eine Aussage über Anthropics Preisliste, die veralten
 * kann, ohne dass irgendetwas kaputtgeht. Deshalb steht er in der Konfiguration neben
 * {@code anthropic.api.model} und nicht als Konstante im Code — eine Preisänderung ist so eine
 * Config-Änderung, kein Deployment. Und deshalb loggt
 * {@link ClaudeCategorizationService} den Betrag lieber gar nicht, als ihn zu raten: Eine Zeile,
 * die eine falsche Zahl behauptet, ist schlechter als eine ohne (dieselbe Regel wie beim MDC,
 * siehe {@code docs/CONVENTIONS.md}).
 *
 * @param inputPerMTok USD pro 1'000'000 Input-Tokens.
 * @param outputPerMTok USD pro 1'000'000 Output-Tokens.
 */
public record ModelPricing(BigDecimal inputPerMTok, BigDecimal outputPerMTok) {

    private static final BigDecimal TOKENS_PER_MILLION = BigDecimal.valueOf(1_000_000L);

    /**
     * Nachkommastellen des geschätzten Betrags.
     *
     * <p>Ein Bündel von 20 Transaktionen kostet auf Haiku 4.5 rund 0.0005 USD — auf Cent gerundet
     * wäre jede Zeile 0.00 und die Summe über einen Import ebenfalls. Sechs Stellen halten auch
     * den einzelnen Call noch unterscheidbar.
     */
    static final int SCALE = 6;

    public ModelPricing {
        if (inputPerMTok == null || outputPerMTok == null) {
            throw new IllegalArgumentException("Preis pro MTok darf nicht null sein");
        }
        if (inputPerMTok.signum() < 0 || outputPerMTok.signum() < 0) {
            throw new IllegalArgumentException("Preis pro MTok darf nicht negativ sein");
        }
    }

    /**
     * Schätzt die Kosten eines Calls.
     *
     * <p>Gilt nur für regulären Input zum Standard-Tarif. Cache-Reads (10 % des Input-Preises),
     * Cache-Writes und der Batch-Rabatt (50 %) sind <em>nicht</em> abgebildet — der Aufrufer muss
     * sicherstellen, dass die Response keine solchen Anteile meldet, sonst schätzt diese Methode
     * zu hoch. {@link ClaudeCategorizationService#logTokenUsage} prüft genau das, bevor es hier
     * hereingeht.
     *
     * @param inputTokens abgerechnete Input-Tokens der Response.
     * @param outputTokens abgerechnete Output-Tokens der Response.
     * @return geschätzte Kosten in USD, auf {@link #SCALE} Stellen gerundet.
     */
    public BigDecimal costFor(long inputTokens, long outputTokens) {
        BigDecimal input = BigDecimal.valueOf(inputTokens).multiply(inputPerMTok);
        BigDecimal output = BigDecimal.valueOf(outputTokens).multiply(outputPerMTok);
        return input.add(output).divide(TOKENS_PER_MILLION, SCALE, RoundingMode.HALF_UP);
    }
}
