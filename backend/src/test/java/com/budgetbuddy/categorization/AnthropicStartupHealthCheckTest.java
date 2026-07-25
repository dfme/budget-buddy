package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.services.blocking.ModelService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Unit-Test des Anthropic-Startup-Healthchecks (INFRA-11) mit gemocktem Client. Bewusst kein
 * echter API-Call — analog zu {@link ClaudeCategorizationServiceTest}: das bräuchte einen Key und
 * wäre in CI nicht reproduzierbar.
 */
@ExtendWith(MockitoExtension.class)
class AnthropicStartupHealthCheckTest {

    @Mock private ObjectProvider<AnthropicClient> clientProvider;
    @Mock private AnthropicClient client;
    @Mock private ModelService modelService;

    @Test
    void doesNothingWhenNoClientBean() {
        // Kein Key -> kein Client-Bean: der Check darf keinen Call absetzen.
        when(clientProvider.getIfAvailable()).thenReturn(null);

        new AnthropicStartupHealthCheck(clientProvider).onApplicationReady();

        verifyNoInteractions(client);
    }

    @Test
    void pingsModelsEndpointWhenClientPresent() {
        when(client.models()).thenReturn(modelService);
        when(modelService.list()).thenReturn(null);

        new AnthropicStartupHealthCheck(clientProvider).ping(client);

        verify(modelService).list();
    }

    @Test
    void doesNotThrowWhenApiCallFails() {
        when(client.models()).thenReturn(modelService);
        when(modelService.list()).thenThrow(new AnthropicException("API down", null));

        AnthropicStartupHealthCheck check = new AnthropicStartupHealthCheck(clientProvider);

        // Ein fehlgeschlagener Healthcheck darf nie den Start abbrechen (nur Log).
        assertThatCode(() -> check.ping(client)).doesNotThrowAnyException();
    }
}
