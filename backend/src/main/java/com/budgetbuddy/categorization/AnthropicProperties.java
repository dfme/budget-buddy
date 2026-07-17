package com.budgetbuddy.categorization;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Konfiguration für die Claude-API-Kategorisierung (BE-CAT-02, ADR-6).
 *
 * <p>Der Key stammt ausschliesslich aus der Umgebungsvariable {@code ANTHROPIC_API_KEY}
 * (gemappt via {@code anthropic.api.key}) und wird nie hardcodiert.
 *
 * <p>Anders als bei {@code JwtProperties} ist der Key bewusst <strong>nicht</strong> mit
 * {@code @NotBlank} validiert: Ein fehlender JWT-Secret wäre ein Sicherheitsproblem und muss den
 * Start verhindern, ein fehlender Anthropic-Key dagegen nicht. Ohne Key soll die App normal
 * starten — {@link ClaudeCategorizationService} degradiert dann auf {@link Category#SONSTIGES},
 * sodass auch ohne Anthropic-Account entwickelt und getestet werden kann.
 *
 * @param key API-Key, oder leer/{@code null}, wenn keiner gesetzt ist.
 * @param model Modell-ID für die Kategorisierung. Default {@link #DEFAULT_MODEL}.
 */
@ConfigurationProperties(prefix = "anthropic.api")
public record AnthropicProperties(String key, String model) {

    /**
     * Schnelles, günstiges Modell für Single-Label-Klassifikation.
     *
     * <p>Nachfolger von Haiku 3.5, dessen ID ({@code claude-3-5-haiku-20241022}) am 19.02.2026
     * abgeschaltet wurde.
     */
    public static final String DEFAULT_MODEL = "claude-haiku-4-5";

    public AnthropicProperties {
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
    }

    /** {@code true}, wenn ein API-Key konfiguriert ist und Claude-Calls möglich sind. */
    public boolean hasKey() {
        return key != null && !key.isBlank();
    }
}
