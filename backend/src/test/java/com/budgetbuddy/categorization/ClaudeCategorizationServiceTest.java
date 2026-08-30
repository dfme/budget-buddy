package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import com.budgetbuddy.categorization.ClaudeCategorizationService.BatchCategorization;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit-Test der {@link ClaudeCategorizationService}-Logik mit gemocktem Anthropic-Client:
 * Bündelung mehrerer Transaktionen pro Request (ADR-14), Mapping der Antwort auf
 * {@link Category}, Fallback auf {@code Sonstiges} bei allen Fehlerpfaden, sowie das
 * Circuit-Breaker-Verhalten.
 *
 * <p>Bewusst kein Integrationstest gegen die echte API: der bräuchte einen gültigen Key, wäre in
 * CI nicht reproduzierbar und würde pro Lauf Kosten verursachen.
 *
 * <p>Der Mock antwortet mit echtem Structured-Output-JSON in einem echten {@link Message}, das
 * über den öffentlichen {@link StructuredMessage}-Konstruktor typisiert wird. Damit läuft die
 * Deserialisierung des SDK im Test wirklich mit — ein Feldname, der nicht zu
 * {@link BatchCategorization} passt, fällt hier auf und nicht erst in Produktion.
 */
@ExtendWith(MockitoExtension.class)
class ClaudeCategorizationServiceTest {

    private static final String TRANSACTION = "DIGITEC GALAXUS AG 044 913 2323";

    @Mock private ObjectProvider<AnthropicClient> clientProvider;
    @Mock private AnthropicClient client;
    @Mock private MessageService messageService;
    @Mock private Clock clock;

    private ClaudeCategorizationService service;

    @BeforeEach
    void setUp() {
        // lenient(), weil nicht jeder Test bis zum Client durchläuft (Blank-Input, fehlender
        // Key). Nur diese Fixture-Stubs sind ausgenommen — die Stubs in den Tests selbst
        // bleiben streng geprüft.
        lenient().when(clientProvider.getIfAvailable()).thenReturn(client);
        lenient().when(client.messages()).thenReturn(messageService);
        lenient().when(clock.millis()).thenReturn(0L);

        service =
                new ClaudeCategorizationService(
                        clientProvider,
                        new AnthropicProperties("test-key", "claude-haiku-4-5"),
                        clock);
    }

    @Test
    void mapsClaudeResponseToCategory() {
        respondWith(Category.LEBENSMITTEL);

        Optional<CategorizationResult> result = service.categorize("MIGROS BERN");

        assertThat(result).contains(claude(Category.LEBENSMITTEL));
    }

    @Test
    void returnsEmptyForBlankInputWithoutCallingClaude() {
        assertThat(service.categorize("   ")).isEmpty();
        assertThat(service.categorize(null)).isEmpty();

        verifyNoInteractions(messageService);
    }

    @Test
    void fallsBackToSonstigesOnAnthropicException() {
        failWith(new AnthropicException("Timeout", null));

        Optional<CategorizationResult> result = service.categorize(TRANSACTION);

        assertThat(result).contains(claude(Category.SONSTIGES));
    }

    @Test
    void fallsBackToSonstigesWhenNoApiKeyConfigured() {
        when(clientProvider.getIfAvailable()).thenReturn(null);

        Optional<CategorizationResult> result = service.categorize(TRANSACTION);

        // Kein Request hinaus → CLAUDE_SKIPPED, nicht CLAUDE (Review PR #174): Die Trennung hält
        // die Laufzeit-Aussage der Import-Summary sauber.
        assertThat(result).contains(skipped());
        verifyNoInteractions(messageService);
    }

    @Test
    void usesConfiguredModel() {
        respondWith(Category.SHOPPING);

        service.categorize(TRANSACTION);

        assertThat(captureParams().model().asString()).isEqualTo("claude-haiku-4-5");
    }

    // --- Bündelung (ADR-14) ---

    /**
     * Der Kern von ADR-14: Mehrere Transaktionen kosten <em>einen</em> Request. Vor BE-PDF-09 war
     * es einer pro Transaktion — bei 108 Transaktionen der Unterschied zwischen einem
     * funktionierenden und einem scheiternden Import (#192).
     */
    @Test
    void categorizesMultipleTransactionsInASingleRequest() {
        respondWith(Category.LEBENSMITTEL, Category.TRANSPORT, Category.RESTAURANT);

        List<Optional<CategorizationResult>> results =
                service.categorizeAll(List.of("MIGROS", "SBB", "PIZZERIA"));

        assertThat(results).containsExactly(
                Optional.of(claude(Category.LEBENSMITTEL)),
                Optional.of(claude(Category.TRANSPORT)),
                Optional.of(claude(Category.RESTAURANT)));
        verify(messageService, times(1)).create(any(StructuredMessageCreateParams.class));
    }

    /**
     * Ein fehlgeschlagener Call kostet immer ein ganzes Bündel. Deshalb geht nicht alles in einen
     * Request, sondern in Portionen von {@link ClaudeCategorizationService#MAX_BATCH_SIZE}.
     */
    @Test
    void splitsLargeInputIntoBatches() {
        int count = ClaudeCategorizationService.MAX_BATCH_SIZE * 2 + 1;
        respondWithSonstigesForEveryNumber();

        service.categorizeAll(texts(count));

        verify(messageService, times(3)).create(any(StructuredMessageCreateParams.class));
    }

    /**
     * Die Rückgabe muss positionsgleich zur Eingabe sein — daran hängt im Import die Zuordnung
     * von Kategorie zu Buchung. Der leere Text in der Mitte ist der interessante Fall: Er geht
     * nicht ins Bündel und darf die Nummerierung der übrigen nicht verschieben.
     */
    @Test
    void resultsStayAlignedWithInputWhenABlankTextIsSkipped() {
        respondWith(Category.LEBENSMITTEL, Category.TRANSPORT);

        List<Optional<CategorizationResult>> results =
                service.categorizeAll(Arrays.asList("MIGROS", "   ", "SBB"));

        assertThat(results).containsExactly(
                Optional.of(claude(Category.LEBENSMITTEL)),
                Optional.empty(),
                Optional.of(claude(Category.TRANSPORT)));
        assertThat(capturedUserPrompt()).contains("1. MIGROS").contains("2. SBB");
    }

    /**
     * Der Preis der Bündelung wird so klein wie möglich gehalten: Eine unvollständige Antwort
     * kostet genau die fehlenden Transaktionen, nicht das ganze Bündel.
     */
    @Test
    void missingNumberInResponseFallsBackToSonstigesForThatTransactionOnly() {
        respondWithJson("""
                {"categories":[{"number":1,"category":"LEBENSMITTEL"},\
                {"number":3,"category":"RESTAURANT"}]}""");

        List<Optional<CategorizationResult>> results =
                service.categorizeAll(List.of("MIGROS", "UNBEKANNT", "PIZZERIA"));

        assertThat(results).containsExactly(
                Optional.of(claude(Category.LEBENSMITTEL)),
                Optional.of(claude(Category.SONSTIGES)),
                Optional.of(claude(Category.RESTAURANT)));
    }

    /** Eine Nummer ausserhalb des Bündels wird ignoriert und darf nichts überschreiben. */
    @Test
    void outOfRangeNumberIsIgnored() {
        respondWithJson("""
                {"categories":[{"number":99,"category":"SHOPPING"},\
                {"number":1,"category":"LEBENSMITTEL"}]}""");

        assertThat(service.categorizeAll(List.of("MIGROS")))
                .containsExactly(Optional.of(claude(Category.LEBENSMITTEL)));
    }

    /** Abgeschnittenes oder unerwartetes JSON darf keine Exception nach aussen lassen. */
    @Test
    void unreadableResponseFallsBackToSonstigesForTheWholeBatch() {
        respondWithJson("{\"categories\":[{\"number\":1,\"cat");

        assertThat(service.categorizeAll(List.of("MIGROS", "SBB"))).containsExactly(
                Optional.of(claude(Category.SONSTIGES)),
                Optional.of(claude(Category.SONSTIGES)));
    }

    /**
     * Die Kategorienliste steht seit ADR-14 im Schema statt im Prompt — geprüft wird sie deshalb
     * dort. Gegen {@link Category#values()} statt gegen eine Literal-Liste, damit der Test auch
     * anschlägt, wenn später eine Kategorie ergänzt wird und das Schema nicht nachzieht. Das ist
     * strenger als die frühere Prompt-Prüfung: Was im Schema steht, ist für das Modell nicht
     * bloss eine Bitte, sondern die einzige Menge, aus der es wählen kann.
     */
    @Test
    void schemaContainsAllCategories() {
        respondWith(Category.SHOPPING);

        service.categorize(TRANSACTION);

        // toString() statt Feldnavigation: Das Schema liegt im SDK als verschachtelte
        // JsonValue-Map, und geprüft wird ohnehin nur, dass jedes Label darin vorkommt.
        String schema = captureParams().outputConfig().orElseThrow().toString();

        assertThat(Category.values()).hasSize(13);
        Arrays.stream(Category.values())
                .forEach(category -> assertThat(schema).contains(category.name()));
    }

    /** Der Transaktionstext muss nummeriert im Prompt stehen — daran hängt die Zuordnung. */
    @Test
    void promptContainsNumberedTransactionTexts() {
        respondWith(Category.SHOPPING);

        service.categorize(TRANSACTION);

        assertThat(capturedUserPrompt()).contains("1. " + TRANSACTION);
    }

    // --- Datenminimierung (BE-CAT-06) ---

    /**
     * Die AC im Wortlaut: ein Text mit IBAN, Kartennummer und Betrag erzeugt einen Prompt, der
     * keines dieser Elemente enthält.
     *
     * <p>Geprüft wird am Prompt, nicht am Sanitizer — {@link PromptSanitizer} hat seine eigenen
     * Tests. Die Frage hier ist, ob er auf dem Weg zur API überhaupt angewendet wird.
     */
    @Test
    void promptContainsNeitherIbanNorCardNumberNorAmount() {
        respondWith(Category.WOHNEN);

        service.categorize("GIRO POST CH7709000000850055555 XXXX4417 MIETE 1'234.56");

        assertThat(capturedUserPrompt())
                .doesNotContain("CH7709000000850055555")
                .doesNotContain("XXXX4417")
                .doesNotContain("1'234.56")
                .contains("<IBAN>", "<KARTE>", "<BETRAG>");
    }

    /**
     * Die Gegenrichtung: Die Maskierung darf die Kategorisierung nicht kaputt machen. Der
     * Zwecktoken {@code MIETE} überlebt, und das Ergebnis kommt unverändert beim Aufrufer an.
     */
    @Test
    void maskedTextStillYieldsTheCorrectCategory() {
        respondWith(Category.WOHNEN);

        Optional<CategorizationResult> result =
                service.categorize("GIRO POST MUSTER, LEA MIETE JANUAR 2025");

        assertThat(result).contains(claude(Category.WOHNEN));
        assertThat(capturedUserPrompt()).contains("MIETE JANUAR 2025").doesNotContain("MUSTER");
    }

    // --- Circuit Breaker ---

    @Test
    void breakerOpensAfterThresholdFailuresAndStopsCallingClaude() {
        failWith(new AnthropicException("API down", null));

        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD; i++) {
            assertThat(service.categorize(TRANSACTION)).contains(claude(Category.SONSTIGES));
        }
        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD))
                .create(any(StructuredMessageCreateParams.class));

        // Ab jetzt darf kein Call mehr rausgehen — der Rest des Imports fällt sofort durch, und
        // zwar als CLAUDE_SKIPPED: ohne Request kostet er auch keine Latenz (Review PR #174).
        assertThat(service.categorize(TRANSACTION)).contains(skipped());
        assertThat(service.categorize(TRANSACTION)).contains(skipped());

        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD))
                .create(any(StructuredMessageCreateParams.class));
    }

    /**
     * Der Breaker greift auch mitten in einem grossen {@code categorizeAll}: Nach drei
     * fehlgeschlagenen Bündeln geht für den Rest kein Request mehr hinaus. Genau dafür ist er da
     * — bei 108 Transaktionen wären es sonst sechs Timeouts hintereinander.
     */
    @Test
    void breakerOpensMidRunAndSkipsRemainingBatches() {
        failWith(new AnthropicException("API down", null));
        int batches = ClaudeCategorizationService.FAILURE_THRESHOLD + 2;

        List<Optional<CategorizationResult>> results =
                service.categorizeAll(texts(ClaudeCategorizationService.MAX_BATCH_SIZE * batches));

        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD))
                .create(any(StructuredMessageCreateParams.class));
        // Die ersten drei Bündel haben Claude erreicht (CLAUDE), die restlichen nicht
        // (CLAUDE_SKIPPED) — kategorisiert ist trotzdem alles.
        assertThat(results).hasSize(ClaudeCategorizationService.MAX_BATCH_SIZE * batches);
        assertThat(results).allMatch(r -> r.orElseThrow().category() == Category.SONSTIGES);
        assertThat(results.get(0)).contains(claude(Category.SONSTIGES));
        assertThat(results.get(results.size() - 1)).contains(skipped());
    }

    @Test
    void successResetsFailureCounter() {
        failWith(new AnthropicException("Blip", null));
        service.categorize(TRANSACTION);
        service.categorize(TRANSACTION);

        respondWith(Category.LEBENSMITTEL);
        assertThat(service.categorize("MIGROS BERN")).contains(claude(Category.LEBENSMITTEL));

        // Zähler steht wieder auf 0: zwei weitere Fehler dürfen den Breaker noch nicht öffnen.
        failWith(new AnthropicException("Blip", null));
        service.categorize(TRANSACTION);
        service.categorize(TRANSACTION);

        respondWith(Category.TRANSPORT);
        assertThat(service.categorize("SBB TICKET")).contains(claude(Category.TRANSPORT));
    }

    @Test
    void breakerAllowsTrialCallAfterCooldownAndClosesOnSuccess() {
        failWith(new AnthropicException("API down", null));
        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD; i++) {
            service.categorize(TRANSACTION);
        }

        // Cooldown noch nicht abgelaufen → kein Call.
        when(clock.millis()).thenReturn(ClaudeCategorizationService.COOLDOWN.toMillis() - 1);
        service.categorize(TRANSACTION);
        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD))
                .create(any(StructuredMessageCreateParams.class));

        // Cooldown abgelaufen → Trial-Call geht raus und schliesst den Breaker.
        when(clock.millis()).thenReturn(ClaudeCategorizationService.COOLDOWN.toMillis() + 1);
        respondWith(Category.LEBENSMITTEL);

        assertThat(service.categorize("MIGROS BERN")).contains(claude(Category.LEBENSMITTEL));
        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD + 1))
                .create(any(StructuredMessageCreateParams.class));
    }

    /**
     * Scheitert der Trial-Call nach dem Cooldown, muss der Breaker sofort wieder öffnen — nicht
     * erst nach {@code FAILURE_THRESHOLD} weiteren Calls. Sonst gingen bei anhaltendem Ausfall
     * pro Cooldown-Zyklus 3 Calls (~60s) statt einem (~20s) gegen die tote API.
     */
    @Test
    void breakerReopensWhenTrialCallFails() {
        failWith(new AnthropicException("API down", null));
        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD; i++) {
            service.categorize(TRANSACTION);
        }

        // Cooldown abgelaufen → genau ein Trial-Call, der ebenfalls scheitert.
        when(clock.millis()).thenReturn(ClaudeCategorizationService.COOLDOWN.toMillis() + 1);
        assertThat(service.categorize(TRANSACTION)).contains(claude(Category.SONSTIGES));

        int callsSoFar = ClaudeCategorizationService.FAILURE_THRESHOLD + 1;
        verify(messageService, times(callsSoFar)).create(any(StructuredMessageCreateParams.class));

        // Breaker muss wieder zu sein: keine weiteren Calls bis zum nächsten Cooldown-Ende.
        service.categorize(TRANSACTION);
        service.categorize(TRANSACTION);
        verify(messageService, times(callsSoFar)).create(any(StructuredMessageCreateParams.class));
    }

    /**
     * Eine unlesbare Antwort ist eine erfolgreiche API-Antwort — kein Infrastruktur-Problem.
     * Würde sie den Breaker öffnen, könnte ein paar Mal danebenliegendes Modell den ganzen
     * restlichen Import auf {@code Sonstiges} kippen, obwohl Claude einwandfrei antwortet.
     */
    @Test
    void unreadableResponseDoesNotOpenBreaker() {
        respondWithJson("kein JSON");

        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD + 2; i++) {
            assertThat(service.categorize(TRANSACTION)).contains(claude(Category.SONSTIGES));
        }

        // Jeder Aufruf hat Claude erreicht — der Breaker ist nie eingesprungen.
        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD + 2))
                .create(any(StructuredMessageCreateParams.class));
    }

    // --- Helpers ---

    private static CategorizationResult claude(Category category) {
        return new CategorizationResult(category, CategorizationResult.Source.CLAUDE);
    }

    /** Claude-Stufe erreicht, aber ohne HTTP-Request beantwortet (Breaker offen / kein Key). */
    private static CategorizationResult skipped() {
        return new CategorizationResult(
                Category.SONSTIGES, CategorizationResult.Source.CLAUDE_SKIPPED);
    }

    private static List<String> texts(int count) {
        return IntStream.range(0, count).mapToObj(i -> "Unbekannter Händler " + i).toList();
    }

    @SuppressWarnings("unchecked")
    private MessageCreateParams captureParams() {
        ArgumentCaptor<StructuredMessageCreateParams<BatchCategorization>> captor =
                ArgumentCaptor.forClass(StructuredMessageCreateParams.class);
        verify(messageService).create(captor.capture());
        return captor.getValue().rawParams();
    }

    /** Liest den User-Prompt aus den erfassten Params — gezielt statt via {@code toString()}. */
    private String capturedUserPrompt() {
        return captureParams().messages().get(0).content().string().orElseThrow();
    }

    /** Antwortet mit einer Kategorie pro übergebener Konstante, in dieser Reihenfolge. */
    private void respondWith(Category... categories) {
        String entries =
                IntStream.range(0, categories.length)
                        .mapToObj(i -> "{\"number\":%d,\"category\":\"%s\"}"
                                .formatted(i + 1, categories[i].name()))
                        .collect(Collectors.joining(","));
        respondWithJson("{\"categories\":[" + entries + "]}");
    }

    /**
     * Antwortet auf jeden Call mit {@code Sonstiges} für die Nummern 1 bis
     * {@link ClaudeCategorizationService#MAX_BATCH_SIZE} — genug für jedes volle Bündel.
     */
    private void respondWithSonstigesForEveryNumber() {
        List<Category> all = new ArrayList<>(
                Collections.nCopies(ClaudeCategorizationService.MAX_BATCH_SIZE,
                        Category.SONSTIGES));
        respondWith(all.toArray(new Category[0]));
    }

    private void respondWithJson(String json) {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenReturn(new StructuredMessage<>(
                        BatchCategorization.class, messageWithText(json)));
    }

    private void failWith(RuntimeException exception) {
        when(messageService.create(any(StructuredMessageCreateParams.class)))
                .thenThrow(exception);
    }

    /**
     * Baut eine echte {@link Message}, wie sie das SDK zurückgibt.
     *
     * <p>Die vielen {@code Optional.empty()} sind kein Zierrat: Der SDK-Builder verlangt auch für
     * nullable Felder einen expliziten Wert und wirft sonst beim {@code build()}.
     */
    private static Message messageWithText(String text) {
        Usage usage =
                Usage.builder()
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
