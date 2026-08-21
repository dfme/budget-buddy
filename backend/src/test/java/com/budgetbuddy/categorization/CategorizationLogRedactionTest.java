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
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.budgetbuddy.categorization.ClaudeCategorizationService.BatchCategorization;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
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
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new AnthropicException("Timeout", null));
        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD; i++) {
            claudeService.categorize(TRANSACTION);
        }
        // DEBUG-Pfad «Circuit Breaker offen».
        claudeService.categorize(TRANSACTION);

        assertRedacted();
    }

    /**
     * Seit ADR-13 kann eine <em>unbekannte Kategorie</em> nicht mehr auftreten — das Schema lässt
     * nur die 13 Enum-Konstanten zu. Der verbleibende Fall ist eine Antwort, die sich nicht lesen
     * lässt; auch sie darf nichts vom Zahlungstext preisgeben.
     */
    @Test
    void claudeUnreadableResponsePathNeverLogsTransactionTextInPlaintext() {
        respondWithJson("   ");
        claudeService.categorize(TRANSACTION);

        assertRedacted();
        // Die Zeile bleibt diagnostisch brauchbar: Sie sagt weiterhin, DASS etwas Unbrauchbares kam.
        assertThat(appender.list)
                .anySatisfy(event ->
                        assertThat(event.getFormattedMessage()).contains("nicht lesbar"));
    }

    /**
     * Eine unvollständige Bündelantwort ist der neue, häufigste Diagnosefall (ADR-13): Sie kostet
     * einzelne Transaktionen ihre Kategorie. Die Zeile nennt deshalb Zahlen — und nur Zahlen.
     */
    @Test
    void claudeIncompleteBatchPathLogsCountsOnly() {
        respondWithJson("{\"categories\":[]}");
        claudeService.categorize(TRANSACTION);

        assertRedacted();
        assertThat(appender.list)
                .anySatisfy(event -> assertThat(event.getFormattedMessage())
                        .contains("Claude beantwortete nur 0 von 1"));
    }

    /**
     * Der Fall, den der PR-Review von #174 als blockierend aufgedeckt hat: Antwortet das Modell
     * mit einem Echo des Prompts (der den Transaktionstext enthält), darf dieses Echo nicht in
     * die Logs geraten. Seit ADR-13 landet ein Echo im Zweig «nicht lesbar» — die Zeile trägt nur
     * den Exception-Typ, nicht den Wortlaut der Antwort.
     */
    @Test
    void claudeEchoingTheTransactionTextIsNotLogged() {
        respondWithJson(TRANSACTION);

        claudeService.categorize(TRANSACTION);

        assertRedacted();
    }

    /**
     * {@code .trim()} entfernt nur aussen: Eine Antwort mit Zeilenumbruch könnte sonst eine
     * zweite, gefälschte Log-Zeile vortäuschen. Die Redaktion schliesst das mit ein.
     */
    @Test
    void claudeResponseWithNewlineCannotForgeALogLine() {
        respondWithJson("Freizeit\nWARN gefälschte Zeile");

        claudeService.categorize(TRANSACTION);

        assertRedacted();
        assertThat(appender.list)
                .allSatisfy(event ->
                        assertThat(event.getFormattedMessage()).doesNotContain("gefälschte Zeile"));
    }

    /**
     * Die SDK-Fehlermeldung ist ein Fremdstring; ob sie Request-Inhalt zurückspiegeln kann, ist
     * nicht belegt. Geloggt wird deshalb nur der Exception-Typ (Review PR #174).
     */
    @Test
    void claudeFailureLogsExceptionTypeInsteadOfSdkMessage() {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenThrow(new AnthropicException("ZAHLUNG KARDIOLOGIE HIRSLANDEN 4242", null));

        claudeService.categorize(TRANSACTION);

        assertRedacted();
        assertThat(appender.list)
                .anySatisfy(event ->
                        assertThat(event.getFormattedMessage()).contains("AnthropicException"));
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
        when(claude.categorizeAll(List.of(TRANSACTION)))
                .thenThrow(new IllegalStateException("SDK kaputt"));
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
                // Innerhalb des Prozesses deterministisch: identische Texte bleiben über
                // Log-Zeilen hinweg korrelierbar.
                .isEqualTo(LogRedaction.redact(TRANSACTION));
        assertThat(LogRedaction.redact(null)).isEqualTo("<null>");
    }

    /**
     * Gesalzen (Review PR #174): Ohne Salt könnte, wer Log-Zugriff hat, eine Vermutung wie
     * «KARDIOLOGIE HIRSLANDEN» nachrechnen und bestätigen. Der Hash darf deshalb nicht der
     * blanke SHA-256 des Texts sein.
     */
    @Test
    void redactionHashIsSaltedAndThereforeNotConfirmableAgainstAGuess() throws Exception {
        String unsaltedPrefix = HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(TRANSACTION.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 8);

        assertThat(LogRedaction.redact(TRANSACTION)).doesNotContain(unsaltedPrefix);
    }

    @Test
    void describeReportsTypeChainWithoutTheMessage() {
        Exception cause = new java.net.SocketTimeoutException("timeout nach 10s");
        Exception wrapped = new AnthropicException("Anfrage an /v1/messages fehlgeschlagen", cause);

        assertThat(LogRedaction.describe(wrapped))
                .isEqualTo("AnthropicException ← SocketTimeoutException")
                .doesNotContain("v1/messages");
        assertThat(LogRedaction.describe(new IllegalStateException("kaputt")))
                .isEqualTo("IllegalStateException");
        assertThat(LogRedaction.describe(null)).isEqualTo("<null>");
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

    /** Antwortet mit dem gegebenen Text als Structured-Output-Nutzlast. */
    private void respondWithJson(String json) {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(new StructuredMessage<>(
                        BatchCategorization.class, messageWithText(json)));
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
