package com.budgetbuddy.categorization;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Zweite Stufe der Hybrid-Kategorisierung (ADR-6): Transaktionen, die {@link LookupTableService}
 * nicht kennt, gehen an die Claude API.
 *
 * <p>Als <strong>letzte</strong> Stufe der Kette liefert dieser Service bei jedem Fehler
 * {@link Category#SONSTIGES} statt {@link Optional#empty()} — ein fehlgeschlagener Claude-Call
 * darf den Import nie blockieren (Churn-Risiko #1). {@link Optional#empty()} kommt nur bei leerer
 * Eingabe zurück, wo es nichts zu kategorisieren gibt.
 *
 * <p><strong>Circuit Breaker:</strong> {@code categorize} verarbeitet eine Transaktion pro Aufruf,
 * ein Import also ~20 Aufrufe nacheinander. Ohne Schutz würde ein API-Ausfall den synchronen
 * Upload-Endpoint minutenlang blockieren (20 × Timeout), obwohl jeder einzelne Fallback korrekt
 * greift. Nach {@link #FAILURE_THRESHOLD} Fehlern in Folge gilt Claude deshalb als nicht
 * erreichbar und alle weiteren Aufrufe werden für {@link #COOLDOWN} ohne HTTP-Request mit
 * {@code Sonstiges} beantwortet. Danach folgt ein Trial-Call: Erfolg schliesst den Breaker,
 * Fehler öffnet ihn erneut.
 */
@Service
public class ClaudeCategorizationService implements CategorizationPort {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCategorizationService.class);

    /** Fehler in Folge, ab denen der Breaker öffnet. */
    static final int FAILURE_THRESHOLD = 3;

    /** Dauer, für die der Breaker nach dem Öffnen geschlossen bleibt. */
    static final Duration COOLDOWN = Duration.ofSeconds(60);

    /**
     * Genug für einen Kategorienamen. Der Prompt verlangt nur das Label; ein knappes Limit
     * verhindert, dass ein abschweifendes Modell Kosten und Latenz treibt.
     */
    private static final long MAX_TOKENS = 20L;

    private static final String SYSTEM_PROMPT =
            """
            Du kategorisierst Schweizer Bankkonto-Transaktionen.
            Antworte ausschliesslich mit genau einem Kategorienamen aus der vorgegebenen Liste.
            Keine Erklärung, keine Satzzeichen, kein weiterer Text.""";

    private final ObjectProvider<AnthropicClient> clientProvider;
    private final AnthropicProperties properties;
    private final Clock clock;

    /** Fehler seit dem letzten Erfolg. Der Service ist ein Singleton — Zustand muss thread-safe sein. */
    private final AtomicInteger consecutiveFailures = new AtomicInteger();

    /** Zeitpunkt (Epoch-Millis), ab dem wieder ein Trial-Call erlaubt ist. 0 = Breaker geschlossen. */
    private final AtomicLong openUntilEpochMillis = new AtomicLong();

    public ClaudeCategorizationService(
            ObjectProvider<AnthropicClient> clientProvider,
            AnthropicProperties properties,
            Clock clock) {
        this.clientProvider = clientProvider;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<Category> categorize(String transactionText) {
        if (transactionText == null || transactionText.isBlank()) {
            return Optional.empty();
        }

        AnthropicClient client = clientProvider.getIfAvailable();
        if (client == null) {
            // Kein API-Key konfiguriert — bereits beim Start geloggt, hier nur noch Fallback.
            return Optional.of(Category.SONSTIGES);
        }

        if (isBreakerOpen()) {
            log.debug("Circuit Breaker offen — '{}' ohne Claude-Call als 'Sonstiges' eingestuft.",
                    transactionText);
            return Optional.of(Category.SONSTIGES);
        }

        Message response;
        try {
            response = client.messages().create(buildParams(transactionText));
        } catch (AnthropicException e) {
            // Infrastruktur-Fehler (Timeout, IO, HTTP): zählt für den Breaker.
            recordFailure();
            log.warn("Claude-Call für '{}' fehlgeschlagen ({}) — Fallback 'Sonstiges'.",
                    transactionText, e.getMessage());
            return Optional.of(Category.SONSTIGES);
        }

        // Ab hier hat die API geantwortet: Erfolg für den Breaker, auch wenn der Inhalt
        // unbrauchbar ist. Ein halluzinierendes Modell ist kein Infrastruktur-Problem und darf
        // den Breaker nicht öffnen.
        recordSuccess();
        return Optional.of(parseCategory(response, transactionText));
    }

    private MessageCreateParams buildParams(String transactionText) {
        return MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .addUserMessage(buildUserPrompt(transactionText))
                .build();
    }

    /**
     * Baut den Prompt inkl. Kategorienliste.
     *
     * <p>Die Liste wird aus {@link Category} generiert, nicht hardcodiert — so können Enum und
     * Prompt nicht auseinanderlaufen, wenn später eine Kategorie dazukommt.
     */
    private String buildUserPrompt(String transactionText) {
        String categories =
                Arrays.stream(Category.values())
                        .map(Category::getLabel)
                        .collect(Collectors.joining(", "));

        return """
               Kategorisiere diese Transaktion in genau eine der folgenden Kategorien:
               [%s]

               Transaktion: "%s"
               Antwort (nur Kategoriename):"""
                .formatted(categories, transactionText);
    }

    /** Liest das Label aus der Antwort; jeder unbrauchbare Inhalt fällt auf {@code Sonstiges}. */
    private Category parseCategory(Message response, String transactionText) {
        Optional<String> text =
                response.content().stream()
                        .flatMap(block -> block.text().stream())
                        .map(textBlock -> textBlock.text().trim())
                        .filter(value -> !value.isEmpty())
                        .findFirst();

        if (text.isEmpty()) {
            log.warn("Claude lieferte für '{}' keine Textantwort — Fallback 'Sonstiges'.",
                    transactionText);
            return Category.SONSTIGES;
        }

        try {
            return Category.fromLabel(text.get());
        } catch (IllegalArgumentException e) {
            log.warn("Claude lieferte für '{}' die unbekannte Kategorie '{}' — Fallback "
                            + "'Sonstiges'.", transactionText, text.get());
            return Category.SONSTIGES;
        }
    }

    /**
     * @return {@code true}, wenn der Breaker offen ist und kein Call abgesetzt werden darf.
     */
    private boolean isBreakerOpen() {
        long openUntil = openUntilEpochMillis.get();
        if (openUntil == 0L) {
            return false;
        }

        if (clock.millis() < openUntil) {
            return true;
        }

        // Cooldown abgelaufen → HALF-OPEN: genau ein Trial-Call darf durch. compareAndSet stellt
        // sicher, dass bei parallelen Aufrufen nur einer das Trial gewinnt.
        if (openUntilEpochMillis.compareAndSet(openUntil, 0L)) {
            log.info("Circuit Breaker Cooldown abgelaufen — Trial-Call an Claude.");
            return false;
        }
        return true;
    }

    private void recordFailure() {
        // Der Zähler wird hier bewusst nicht zurückgesetzt: Er bleibt auf/über dem Schwellwert,
        // solange kein Call gelingt. Nur so öffnet auch ein fehlschlagender Trial-Call nach dem
        // Cooldown den Breaker sofort wieder, statt erst nach FAILURE_THRESHOLD weiteren Calls.
        // Zurückgesetzt wird ausschliesslich durch einen Erfolg (recordSuccess).
        if (consecutiveFailures.incrementAndGet() >= FAILURE_THRESHOLD) {
            openUntilEpochMillis.set(clock.millis() + COOLDOWN.toMillis());
            log.warn("Claude fehlgeschlagen (≥{}× in Folge) — Circuit Breaker für {}s geöffnet, "
                            + "weitere Transaktionen werden ohne Call als 'Sonstiges' eingestuft.",
                    FAILURE_THRESHOLD, COOLDOWN.toSeconds());
        }
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
    }
}
