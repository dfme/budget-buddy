package com.budgetbuddy.categorization;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.UnauthorizedException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup-Healthcheck für die Anthropic-API (INFRA-11).
 *
 * <p>{@link com.budgetbuddy.config.AnthropicConfig} loggt beim Start nur, ob ein API-Key
 * <em>gesetzt</em> ist — nicht, ob er <em>gültig</em> ist. Ein vertippter oder widerrufener Key
 * sieht am Startup gesund aus und fällt sonst erst beim ersten echten Import auf. Diese Komponente
 * setzt einmalig einen kostenlosen {@code GET /v1/models}-Call ab und loggt das Ergebnis; ein
 * gültiger Key liefert 200, ein ungültiger 401.
 *
 * <p><strong>Prüft die Key-Gültigkeit, nicht das Guthaben.</strong> Ein leeres Guthaben zeigt sich
 * beim ersten Import (WARN + Fallback in {@link ClaudeCategorizationService}) und über den
 * funktionalen Verifikations-Test in {@code backend/README.md}. Ein Guthaben-Snapshot beim Start
 * hätte geringen Mehrwert und würde einen kostenpflichtigen {@code messages}-Call erfordern.
 *
 * <p>Der Check läuft <strong>asynchron</strong> nach {@link ApplicationReadyEvent} und blockiert
 * den Start nie: Er meldet ausschliesslich ins Log, ein Fehler verhindert das Hochfahren nicht
 * (dieselbe Fallback-Philosophie wie beim Import selbst, Churn-Risiko #1). Ohne konfigurierten
 * Key existiert kein {@link AnthropicClient}-Bean und der Check tut nichts.
 */
@Component
public class AnthropicStartupHealthCheck {

    private static final Logger log = LoggerFactory.getLogger(AnthropicStartupHealthCheck.class);

    private final ObjectProvider<AnthropicClient> clientProvider;

    public AnthropicStartupHealthCheck(ObjectProvider<AnthropicClient> clientProvider) {
        this.clientProvider = clientProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        AnthropicClient client = clientProvider.getIfAvailable();
        if (client == null) {
            // Kein Key -> kein Client-Bean; AnthropicConfig hat das bereits als WARN geloggt.
            return;
        }

        // Asynchron: der Call darf das Hochfahren nicht verzögern.
        CompletableFuture.runAsync(() -> ping(client));
    }

    /** Setzt den kostenlosen Gültigkeits-Call ab und loggt das Ergebnis (nie werfend). */
    void ping(AnthropicClient client) {
        try {
            client.models().list();
            log.info("Anthropic-Healthcheck OK — API-Key gültig.");
        } catch (UnauthorizedException e) {
            log.warn(
                    "Anthropic-Healthcheck: API-Key ungültig oder widerrufen (HTTP 401). "
                            + "Kategorisierung läuft produktiv im Fallback ('Sonstiges').");
        } catch (AnthropicException e) {
            log.warn("Anthropic-Healthcheck: Anthropic-API nicht erreichbar ({}).", e.getMessage());
        }
    }
}
