package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.Headers;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.services.blocking.ModelService;
import com.budgetbuddy.support.ThreadScopedLogAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit-Test des Anthropic-Startup-Healthchecks (INFRA-11) mit gemocktem Client. Bewusst kein
 * echter API-Call — analog zu {@link ClaudeCategorizationServiceTest}: das bräuchte einen Key und
 * wäre in CI nicht reproduzierbar.
 *
 * <p>Die drei {@code catch}-Zweige werden loggseitig festgenagelt (INFO gültig / WARN 401 / WARN
 * nicht erreichbar), damit die 401-spezifische Meldung nachweislich vom generischen Zweig
 * unterschieden wird — ein reiner „wirft nicht durch"-Test würde beide Zweige nicht auseinander
 * halten.
 *
 * <p>Weil diese Assertions auf einem prozessweit geteilten Logger sitzen, hängt der Test einen
 * {@link ThreadScopedLogAppender} an und nicht den nackten {@code ListAppender}: Sonst zählen
 * auch Zeilen mit, die ein fremder Thread zufällig gleichzeitig schreibt — die Ursache der
 * Ordnungsabhängigkeit aus #162 (BE-CAT-07).
 */
@ExtendWith(MockitoExtension.class)
class AnthropicStartupHealthCheckTest {

    @Mock private ObjectProvider<AnthropicClient> clientProvider;
    @Mock private AnthropicClient client;
    @Mock private ModelService modelService;

    private Logger logger;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void attachLogAppender() {
        logger = (Logger) LoggerFactory.getLogger(AnthropicStartupHealthCheck.class);
        logs = new ThreadScopedLogAppender();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detachLogAppender() {
        logger.detachAppender(logs);
    }

    @Test
    void doesNothingWhenNoClientBean() {
        // Kein Key -> kein Client-Bean: der Check darf keinen Call absetzen und nichts loggen.
        when(clientProvider.getIfAvailable()).thenReturn(null);

        new AnthropicStartupHealthCheck(clientProvider).onApplicationReady();

        verifyNoInteractions(client);
        assertThat(logs.list).isEmpty();
    }

    @Test
    void onApplicationReadyRunsCheckAsynchronouslyWhenClientPresent() {
        when(clientProvider.getIfAvailable()).thenReturn(client);
        when(client.models()).thenReturn(modelService);
        when(modelService.list()).thenReturn(null);

        new AnthropicStartupHealthCheck(clientProvider).onApplicationReady();

        // runAsync -> ping() läuft auf einem anderen Thread; timeout() wartet deterministisch,
        // ohne den Test von der Ausführungsgeschwindigkeit abhängig zu machen.
        verify(modelService, timeout(2000)).list();
    }

    @Test
    void logsInfoWhenKeyValid() {
        when(client.models()).thenReturn(modelService);
        when(modelService.list()).thenReturn(null);

        new AnthropicStartupHealthCheck(clientProvider).ping(client);

        assertThat(messagesAt(Level.INFO)).anyMatch(m -> m.contains("API-Key gültig"));
        assertThat(messagesAt(Level.WARN)).isEmpty();
    }

    @Test
    void logsSpecific401WarnWhenKeyInvalid() {
        when(client.models()).thenReturn(modelService);
        when(modelService.list()).thenThrow(unauthorized());

        AnthropicStartupHealthCheck check = new AnthropicStartupHealthCheck(clientProvider);

        // Darf nie durchwerfen ...
        assertThatCode(() -> check.ping(client)).doesNotThrowAnyException();
        // ... und muss die 401-spezifische Zeile loggen, nicht die generische. Das noneMatch
        // belegt, dass der 401-Zweig genommen wurde und die Exception nicht in den generischen
        // catch(AnthropicException) fiel.
        assertThat(messagesAt(Level.WARN)).anyMatch(m -> m.contains("ungültig oder widerrufen"));
        assertThat(messagesAt(Level.WARN)).noneMatch(m -> m.contains("nicht erreichbar"));
    }

    @Test
    void logsGenericWarnWhenApiUnreachable() {
        when(client.models()).thenReturn(modelService);
        when(modelService.list()).thenThrow(new AnthropicException("API down", null));

        AnthropicStartupHealthCheck check = new AnthropicStartupHealthCheck(clientProvider);

        assertThatCode(() -> check.ping(client)).doesNotThrowAnyException();
        assertThat(messagesAt(Level.WARN)).anyMatch(m -> m.contains("nicht erreichbar"));
        assertThat(messagesAt(Level.WARN)).noneMatch(m -> m.contains("ungültig oder widerrufen"));
    }

    /**
     * Regression zu #162: Der Appender darf keine Zeile aufnehmen, die ein <em>fremder</em> Thread
     * auf denselben Logger schreibt.
     *
     * <p>Nachgestellt wird exakt der gemeldete Ablauf: Ein
     * {@code CompletableFuture.runAsync}-Thread aus einem anderen Spring-Kontext lieferte die
     * generische WARN-Zeile nach, nachdem seine Testklasse längst beendet war — und liess damit
     * das {@code noneMatch} in {@link #logsSpecific401WarnWhenKeyInvalid()} fehlschlagen, obwohl
     * dieser Test die Zeile nie erzeugt hatte.
     *
     * <p>Der Test würde mit einem nackten {@code ListAppender} fehlschlagen; er ist damit der
     * eigentliche Nachweis für die Wirkung von {@link ThreadScopedLogAppender}.
     */
    @Test
    void ignoresLogEventsFromForeignThreads() throws InterruptedException {
        Thread foreign = new Thread(
                () -> LoggerFactory.getLogger(AnthropicStartupHealthCheck.class)
                        .warn("Anthropic-Healthcheck: Anthropic-API nicht erreichbar "
                                + "(Fremd-Thread)."),
                "foreign-context-thread");
        foreign.start();
        // join() vor der Assertion: ohne das prüfte der Test nur, dass der Thread noch nicht
        // fertig war, und wäre selbst zeitabhängig — genau der Fehler, den er behebt.
        foreign.join();

        assertThat(logs.list).isEmpty();
        assertThat(messagesAt(Level.WARN)).isEmpty();
    }

    // --- Helpers ---

    /** Echte {@link UnauthorizedException} über den SDK-Builder (die Klasse ist {@code final}). */
    private static UnauthorizedException unauthorized() {
        return UnauthorizedException.builder()
                .headers(Headers.builder().build())
                .body(JsonValue.from("unauthorized"))
                .build();
    }

    private List<String> messagesAt(Level level) {
        return logs.list.stream()
                .filter(event -> event.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }
}
