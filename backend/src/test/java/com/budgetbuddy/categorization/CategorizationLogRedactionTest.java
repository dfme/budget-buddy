package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Regressionstest für die Datenminimierung (BE-PDF-06, Risiko #2, nDSG): Kein Log-Pfad der
 * Kategorisierung darf den Transaktionstext oder das Händler-Pattern im Klartext ausgeben — auf
 * keinem Level, damit auch ein zur Fehlersuche hochgedrehtes Log-Level keine Zahlungsdaten
 * freilegt. Ohne diesen Test wandert der Klartext beim nächsten Fehlersuche-Commit zurück.
 *
 * <p>Geprüft wird der formatierte Log-Output über einen Logback-{@link ListAppender} — genau das,
 * was in den Render-Logs stünde.
 */
@ExtendWith(MockitoExtension.class)
class CategorizationLogRedactionTest {

    /** Frei erfundener, eindeutig wiedererkennbarer Zahlungstext. */
    private static final String TRANSACTION = "ZAHLUNG KARDIOLOGIE HIRSLANDEN 4242";

    @Mock private ObjectProvider<AnthropicClient> clientProvider;
    @Mock private AnthropicClient client;
    @Mock private MessageService messageService;
    @Mock private Clock clock;

    private ClaudeCategorizationService claudeService;
    private ListAppender<ILoggingEvent> appender;
    private final List<Logger> observedLoggers = List.of(
            (Logger) LoggerFactory.getLogger(ClaudeCategorizationService.class),
            (Logger) LoggerFactory.getLogger(HybridCategorizationService.class),
            (Logger) LoggerFactory.getLogger(CategoryLearningService.class));

    @BeforeEach
    void setUp() {
        lenient().when(clientProvider.getIfAvailable()).thenReturn(client);
        lenient().when(client.messages()).thenReturn(messageService);
        lenient().when(clock.millis()).thenReturn(0L);
        claudeService = new ClaudeCategorizationService(
                clientProvider, new AnthropicProperties("test-key", "claude-haiku-4-5"), clock);

        appender = new ListAppender<>();
        appender.start();
        for (Logger logger : observedLoggers) {
            // DEBUG erzwingen: Die DEBUG-Pfade sind in Prod unsichtbar, aber genau das Szenario
            // «Log-Level zur Fehlersuche hochgedreht» soll hier abgesichert sein.
            logger.setLevel(Level.DEBUG);
            logger.addAppender(appender);
        }
    }

    @AfterEach
    void tearDown() {
        for (Logger logger : observedLoggers) {
            logger.detachAppender(appender);
            logger.setLevel(null);
        }
    }

    @Test
    void claudeFailurePathsNeverLogTransactionTextInPlaintext() {
        // WARN-Pfad «Claude-Call fehlgeschlagen» — 3× löst zugleich den Breaker aus.
        when(messageService.create(any(MessageCreateParams.class)))
                .thenThrow(new AnthropicException("Timeout", null));
        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD; i++) {
            claudeService.categorize(TRANSACTION);
        }
        // DEBUG-Pfad «Circuit Breaker offen».
        claudeService.categorize(TRANSACTION);

        assertRedacted();
    }

    @Test
    void claudeEmptyAndUnknownResponsePathsNeverLogTransactionTextInPlaintext() {
        // WARN-Pfad «keine Textantwort».
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(messageWithText("   "));
        claudeService.categorize(TRANSACTION);

        // WARN-Pfad «unbekannte Kategorie» — das Antwort-Label selbst darf im Log stehen
        // (Modell-Output, Diagnosezweck der Zeile), der Transaktionstext nicht.
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(messageWithText("Kryptowährung"));
        claudeService.categorize(TRANSACTION);

        assertRedacted();
        assertThat(appender.list)
                .anySatisfy(event ->
                        assertThat(event.getFormattedMessage()).contains("Kryptowährung"));
    }

    @Test
    void hybridPathsNeverLogTransactionTextInPlaintext() {
        LookupTableService lookup = mock(LookupTableService.class);
        ClaudeCategorizationService claude = mock(ClaudeCategorizationService.class);
        HybridCategorizationService hybrid = new HybridCategorizationService(lookup, claude);

        // DEBUG-Pfad «via Lookup-Tabelle kategorisiert».
        when(lookup.categorize(TRANSACTION)).thenReturn(Optional.of(
                new CategorizationResult(Category.GESUNDHEIT, CategorizationResult.Source.LOOKUP)));
        hybrid.categorize(TRANSACTION);

        // WARN-Pfad «Unerwarteter Fehler bei der Claude-Kategorisierung».
        when(lookup.categorize(TRANSACTION)).thenReturn(Optional.empty());
        when(claude.categorize(TRANSACTION)).thenThrow(new IllegalStateException("SDK kaputt"));
        hybrid.categorize(TRANSACTION);

        assertRedacted();
    }

    @Test
    void learningPathNeverLogsMerchantPatternInPlaintext() {
        CategoryLookupRepository repository = mock(CategoryLookupRepository.class);
        CategoryLearningService learningService = new CategoryLearningService(repository);

        // DEBUG-Pfad «Lookup gelernt» — das Pattern stammt aus dem Transaktionstext.
        learningService.learn(TRANSACTION, Category.GESUNDHEIT);

        assertRedacted();
    }

    @Test
    void redactionFormatIsCorrelatableButNotReconstructible() {
        String redacted = LogRedaction.redact(TRANSACTION);

        assertThat(redacted)
                .matches("<len=" + TRANSACTION.length() + " sha256=[0-9a-f]{8}>")
                // Deterministisch: identische Texte bleiben über Log-Zeilen hinweg korrelierbar.
                .isEqualTo(LogRedaction.redact(TRANSACTION));
        assertThat(LogRedaction.redact(null)).isEqualTo("<null>");
    }

    /**
     * Kein Log-Eintrag darf ein Fragment des Zahlungstexts enthalten. Geprüft wird auf das
     * markante Token «HIRSLANDEN» statt auf den Gesamtstring — so schlägt der Test auch an, wenn
     * ein Pfad den Text gekürzt oder eingebettet ausgibt.
     */
    private void assertRedacted() {
        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list)
                .allSatisfy(event ->
                        assertThat(event.getFormattedMessage()).doesNotContain("HIRSLANDEN"));
    }

    /** Baut eine echte SDK-{@link Message} — Builder analog {@link ClaudeCategorizationServiceTest}. */
    private static Message messageWithText(String text) {
        Usage usage = Usage.builder()
                .cacheCreation(Optional.empty())
                .cacheCreationInputTokens(Optional.empty())
                .cacheReadInputTokens(Optional.empty())
                .inferenceGeo(Optional.empty())
                .inputTokens(50L)
                .outputTokens(3L)
                .serverToolUse(Optional.empty())
                .serviceTier(Optional.empty())
                .build();

        return Message.builder()
                .id("msg_test")
                .container(Optional.empty())
                .model("claude-haiku-4-5")
                .role(JsonValue.from("assistant"))
                .type(JsonValue.from("message"))
                .addContent(TextBlock.builder().text(text).citations(List.of()).build())
                .stopDetails(Optional.empty())
                .stopReason(StopReason.END_TURN)
                .stopSequence(Optional.empty())
                .usage(usage)
                .build();
    }
}
