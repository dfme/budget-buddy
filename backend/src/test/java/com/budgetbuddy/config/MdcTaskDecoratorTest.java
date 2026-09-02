package com.budgetbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Sichert den {@code @Async}-Teil von INFRA-37 ab: Der Importlauf (ADR-14) startet auf einem
 * Pool-Thread und muss die User-ID des auslösenden Requests trotzdem im Log tragen.
 *
 * <p>Geprüft wird die MDC-Map im Task selbst, nicht der Log-Output. {@code ThreadScopedLogAppender}
 * sieht per Konstruktion keine Events fremder Threads — und ein Appender, der Fremd-Threads
 * mitnimmt, hat #162 ausgelöst.
 *
 * <p>Der zweite Testfall ist der eigentliche Grund für das {@code finally} im Decorator: Ein Pool
 * hat wenige Threads und viele Jobs. Bliebe der Kontext stehen, liefe der nächste Import unter der
 * User-ID des vorherigen.
 */
class MdcTaskDecoratorTest {

    private final ThreadPoolTaskExecutor executor = executorWithDecorator();

    @AfterEach
    void tearDown() {
        executor.shutdown();
        MDC.clear();
    }

    @Test
    void carriesTheCallerContextIntoThePoolThread() throws Exception {
        LogContext.putUserId(42L);

        Map<String, String> insideTask = runAndCapture(executor);

        assertThat(insideTask)
                .as("der Pool-Thread kennt die User-ID des Requests, der den Job eingereiht hat")
                .containsEntry(LogContext.USER_ID, "42");
    }

    @Test
    void leavesThePoolThreadCleanForTheNextTask() throws Exception {
        LogContext.putUserId(42L);
        runAndCapture(executor);
        MDC.clear();

        // Zweiter Task ohne Kontext beim Aufrufer — er darf nichts vom ersten vorfinden.
        Map<String, String> insideSecondTask = runAndCapture(executor);

        assertThat(insideSecondTask)
                .as("kein Übertrag zwischen zwei Jobs auf demselben Pool-Thread")
                .isEmpty();
    }

    @Test
    void importExecutorIsWiredWithTheDecorator() {
        // Ohne diesen Test bliebe die Verdrahtung ungeprüft: Die beiden Tests oben bauen ihren
        // eigenen Executor und blieben grün, wenn jemand setTaskDecorator(...) aus AsyncConfig
        // entfernte — der Importlauf verlöre die User-ID im Log, und nichts würde rot.
        new ApplicationContextRunner()
                .withUserConfiguration(AsyncConfig.class)
                .run(context -> {
                    Executor importExecutor =
                            context.getBean(AsyncConfig.IMPORT_EXECUTOR, Executor.class);
                    LogContext.putUserId(42L);

                    assertThat(runAndCapture(importExecutor))
                            .as("der produktive Import-Pool trägt den Kontext des Requests")
                            .containsEntry(LogContext.USER_ID, "42");
                });
    }

    /** Führt einen Task aus und gibt dessen MDC-Stand zurück. */
    private static Map<String, String> runAndCapture(Executor executor)
            throws InterruptedException {
        AtomicReference<Map<String, String>> seen = new AtomicReference<>(Map.of());
        CountDownLatch done = new CountDownLatch(1);
        executor.execute(() -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            seen.set(context == null ? Map.of() : context);
            done.countDown();
        });
        assertThat(done.await(5, TimeUnit.SECONDS)).as("Task lief durch").isTrue();
        return seen.get();
    }

    /** Ein-Thread-Pool: Nur so ist «derselbe Pool-Thread» im zweiten Test überhaupt garantiert. */
    private static ThreadPoolTaskExecutor executorWithDecorator() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.initialize();
        return executor;
    }
}
