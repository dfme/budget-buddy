package com.budgetbuddy.categorization;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

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
 * <p>Die Preistabelle ({@code pricing}) ist optional und dient allein dem Kosten-Logging
 * (BE-CAT-09). Sie steht in der Konfiguration statt im Code, weil ein Preis eine Aussage über
 * Anthropics Preisliste ist und veralten kann — Begründung in {@link ModelPricing}. Fehlt zu
 * einem Modell ein Eintrag, loggt {@link ClaudeCategorizationService} nur die Tokens; die
 * Kategorisierung selbst hängt nie daran.
 *
 * @param key API-Key, oder leer/{@code null}, wenn keiner gesetzt ist.
 * @param model Modell-ID für die Kategorisierung. Default {@link #DEFAULT_MODEL}.
 * @param pricing Preis je Modell-ID, oder leer. Schlüssel ist die Modell-ID, wie sie entweder in
 *     der API-Antwort steht oder unter {@code anthropic.api.model} konfiguriert ist — siehe
 *     {@link #pricingFor(String)}.
 */
@ConfigurationProperties(prefix = "anthropic.api")
public record AnthropicProperties(String key, String model, Map<String, ModelPricing> pricing) {

    /**
     * Schnelles, günstiges Modell für Single-Label-Klassifikation.
     *
     * <p>Nachfolger von Haiku 3.5, dessen ID ({@code claude-3-5-haiku-20241022}) am 19.02.2026
     * abgeschaltet wurde.
     */
    public static final String DEFAULT_MODEL = "claude-haiku-4-5";

    /**
     * {@code @ConstructorBinding} ist hier <strong>nicht</strong> optional: Sobald der Record
     * einen zweiten Konstruktor hat (den Konvenienzkonstruktor unten), ist für Spring nicht mehr
     * eindeutig, welcher gebunden werden soll. Ohne die Annotation fällt das Binding auf
     * JavaBean-Semantik zurück und scheitert beim Start mit «No default constructor found» — und
     * zwar in <em>jedem</em> Spring-Kontext, nicht nur in dem, der die Preise braucht.
     */
    @ConstructorBinding
    public AnthropicProperties {
        if (model == null || model.isBlank()) {
            model = DEFAULT_MODEL;
        }
        pricing = pricing == null ? Map.of() : Map.copyOf(pricing);
    }

    /** Ohne Preistabelle — der Normalfall überall dort, wo nur Key und Modell zählen. */
    public AnthropicProperties(String key, String model) {
        this(key, model, Map.of());
    }

    /** {@code true}, wenn ein API-Key konfiguriert ist und Claude-Calls möglich sind. */
    public boolean hasKey() {
        return key != null && !key.isBlank();
    }

    /**
     * Sucht den Preis für das Modell, das eine Antwort geliefert hat.
     *
     * <p><strong>Zweistufig, und das ist keine Bequemlichkeit:</strong> Für Modelle vor der
     * 4.6-Generation ist die konfigurierte ID ein <em>Alias</em>. {@code claude-haiku-4-5} löst auf
     * {@code claude-haiku-4-5-20251001} auf, und genau diese datierte ID steht in der Antwort.
     * Ein Lookup allein über {@code responseModel} träfe den in {@code anthropic.api.model}
     * konfigurierten Alias deshalb nie, und die Kosten fehlten bei jedem Call. Umgekehrt ist die
     * Antwort die genauere Angabe, wenn sie hinterlegt ist — deshalb hat sie den Vortritt.
     *
     * @param responseModel Modell-ID aus der API-Antwort, oder {@code null}.
     * @return der Preis, oder {@code null}, wenn für keine der beiden IDs einer hinterlegt ist.
     */
    public ModelPricing pricingFor(String responseModel) {
        ModelPricing exact = responseModel == null ? null : pricing.get(responseModel);
        return exact != null ? exact : pricing.get(model);
    }
}
