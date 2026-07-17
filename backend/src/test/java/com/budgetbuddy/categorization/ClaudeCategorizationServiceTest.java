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
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;
import com.anthropic.services.blocking.MessageService;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit-Test der {@link ClaudeCategorizationService}-Logik mit gemocktem Anthropic-Client:
 * Mapping der Claude-Antwort auf {@link Category}, Fallback auf {@code Sonstiges} bei allen
 * Fehlerpfaden, sowie das Circuit-Breaker-Verhalten.
 *
 * <p>Bewusst kein Integrationstest gegen die echte API: der bräuchte einen gültigen Key, wäre in
 * CI nicht reproduzierbar und würde pro Lauf Kosten verursachen.
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
        respondWith("Lebensmittel");

        Optional<Category> result = service.categorize("MIGROS BERN");

        assertThat(result).contains(Category.LEBENSMITTEL);
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

        Optional<Category> result = service.categorize(TRANSACTION);

        assertThat(result).contains(Category.SONSTIGES);
    }

    @Test
    void fallsBackToSonstigesWhenClaudeReturnsUnknownCategory() {
        respondWith("Kryptowährung");

        Optional<Category> result = service.categorize(TRANSACTION);

        assertThat(result).contains(Category.SONSTIGES);
    }

    @Test
    void fallsBackToSonstigesWhenNoApiKeyConfigured() {
        when(clientProvider.getIfAvailable()).thenReturn(null);

        Optional<Category> result = service.categorize(TRANSACTION);

        assertThat(result).contains(Category.SONSTIGES);
        verifyNoInteractions(messageService);
    }

    /**
     * Der Prompt muss alle Kategorien enthalten (Acceptance Criteria). Geprüft wird gegen
     * {@link Category#values()} statt gegen eine Literal-Liste — so schlägt der Test auch an, wenn
     * später eine Kategorie ergänzt und der Prompt nicht nachgezogen wird.
     */
    @Test
    void promptContainsAllCategories() {
        respondWith("Shopping");

        service.categorize(TRANSACTION);

        String prompt = capturedUserPrompt();

        assertThat(Category.values()).hasSize(13);
        assertThat(prompt).contains(TRANSACTION);
        Arrays.stream(Category.values())
                .forEach(category -> assertThat(prompt).contains(category.getLabel()));
    }

    @Test
    void usesConfiguredModel() {
        respondWith("Shopping");

        service.categorize(TRANSACTION);

        assertThat(captureParams().model().asString()).isEqualTo("claude-haiku-4-5");
    }

    // --- Circuit Breaker ---

    @Test
    void breakerOpensAfterThresholdFailuresAndStopsCallingClaude() {
        failWith(new AnthropicException("API down", null));

        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD; i++) {
            assertThat(service.categorize(TRANSACTION)).contains(Category.SONSTIGES);
        }
        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD))
                .create(any(MessageCreateParams.class));

        // Ab jetzt darf kein Call mehr rausgehen — der Rest des Imports fällt sofort durch.
        assertThat(service.categorize(TRANSACTION)).contains(Category.SONSTIGES);
        assertThat(service.categorize(TRANSACTION)).contains(Category.SONSTIGES);

        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD))
                .create(any(MessageCreateParams.class));
    }

    @Test
    void successResetsFailureCounter() {
        failWith(new AnthropicException("Blip", null));
        service.categorize(TRANSACTION);
        service.categorize(TRANSACTION);

        respondWith("Lebensmittel");
        assertThat(service.categorize("MIGROS BERN")).contains(Category.LEBENSMITTEL);

        // Zähler steht wieder auf 0: zwei weitere Fehler dürfen den Breaker noch nicht öffnen.
        failWith(new AnthropicException("Blip", null));
        service.categorize(TRANSACTION);
        service.categorize(TRANSACTION);

        respondWith("Transport");
        assertThat(service.categorize("SBB TICKET")).contains(Category.TRANSPORT);
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
                .create(any(MessageCreateParams.class));

        // Cooldown abgelaufen → Trial-Call geht raus und schliesst den Breaker.
        when(clock.millis()).thenReturn(ClaudeCategorizationService.COOLDOWN.toMillis() + 1);
        respondWith("Lebensmittel");

        assertThat(service.categorize("MIGROS BERN")).contains(Category.LEBENSMITTEL);
        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD + 1))
                .create(any(MessageCreateParams.class));
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
        assertThat(service.categorize(TRANSACTION)).contains(Category.SONSTIGES);

        int callsSoFar = ClaudeCategorizationService.FAILURE_THRESHOLD + 1;
        verify(messageService, times(callsSoFar)).create(any(MessageCreateParams.class));

        // Breaker muss wieder zu sein: keine weiteren Calls bis zum nächsten Cooldown-Ende.
        service.categorize(TRANSACTION);
        service.categorize(TRANSACTION);
        verify(messageService, times(callsSoFar)).create(any(MessageCreateParams.class));
    }

    /**
     * Eine halluzinierte Kategorie ist eine erfolgreiche API-Antwort — kein Infrastruktur-Problem.
     * Würde sie den Breaker öffnen, könnte ein paar Mal danebenliegendes Modell den ganzen
     * restlichen Import auf {@code Sonstiges} kippen, obwohl Claude einwandfrei antwortet.
     */
    @Test
    void hallucinatedCategoryDoesNotOpenBreaker() {
        respondWith("Kryptowährung");

        for (int i = 0; i < ClaudeCategorizationService.FAILURE_THRESHOLD + 2; i++) {
            assertThat(service.categorize(TRANSACTION)).contains(Category.SONSTIGES);
        }

        // Jeder Aufruf hat Claude erreicht — der Breaker ist nie eingesprungen.
        verify(messageService, times(ClaudeCategorizationService.FAILURE_THRESHOLD + 2))
                .create(any(MessageCreateParams.class));
    }

    // --- Helpers ---

    private MessageCreateParams captureParams() {
        ArgumentCaptor<MessageCreateParams> captor =
                ArgumentCaptor.forClass(MessageCreateParams.class);
        verify(messageService).create(captor.capture());
        return captor.getValue();
    }

    /** Liest den User-Prompt aus den erfassten Params — gezielt statt via {@code toString()}. */
    private String capturedUserPrompt() {
        return captureParams().messages().get(0).content().string().orElseThrow();
    }

    private void respondWith(String categoryLabel) {
        when(messageService.create(any(MessageCreateParams.class)))
                .thenReturn(messageWithText(categoryLabel));
    }

    private void failWith(RuntimeException exception) {
        when(messageService.create(any(MessageCreateParams.class))).thenThrow(exception);
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
