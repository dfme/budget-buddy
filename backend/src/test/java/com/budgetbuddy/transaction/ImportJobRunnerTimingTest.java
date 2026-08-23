package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Empirischer Nachweis, dass die Kategorisierung seit ADR-14 <strong>gebündelt</strong> läuft —
 * das Gegenstück zum früheren {@code PdfImportServiceTimingTest}, der für BE-PDF-08 genau das
 * Umgekehrte belegte: strikt sequenziell, ein Call pro Transaktion.
 *
 * <p>Diese Sequenzialität war die Ursache von #192. Ein Auszug mit 108 Transaktionen kostete ~41
 * Round-Trips à ~1.1s und sprengte damit jedes vertretbare Zeitbudget. Der Test hält die
 * Korrektur fest, damit sie nicht unbemerkt zurückgedreht wird — etwa indem jemand
 * {@code categorizeAll} durch eine Schleife über {@code categorize} ersetzt.
 *
 * <p>Im Unterschied zu {@link ImportJobRunnerTest} läuft hier eine echte {@link Clock} (kein
 * deterministisches Mock): erst damit misst {@code Thread.sleep} in der gefakten
 * {@link CategorizationPort} eine reale Wall-Clock-Dauer. Verzögerungen sind bewusst klein
 * gehalten, damit der Test schnell bleibt, ohne den Mechanismus zu verfälschen.
 */
class ImportJobRunnerTimingTest {

    private static final long USER_ID = 42L;
    private static final String SHA = "abc123";
    private static final int BATCH_SIZE = 20;
    private static final long WATCHDOG_SECONDS = 300L;

    private final CategorizationPort categorizationPort = mock(CategorizationPort.class);
    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final ImportJobRepository importJobRepository = mock(ImportJobRepository.class);
    private final TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    private final ImportJobRunner runner = new ImportJobRunner(categorizationPort, repository,
            importJobRepository, transactionTemplate, Clock.systemUTC(), WATCHDOG_SECONDS,
            BATCH_SIZE);

    private static List<ParsedTransaction> unknownTransactions(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new ParsedTransaction(LocalDate.of(2026, 7, 1),
                        "Unbekannter Händler " + i, List.of(), new BigDecimal("10.00"), false))
                .toList();
    }

    /** Simuliert einen Claude-Call, der {@code delay} braucht — unabhängig von der Bündelgrösse. */
    private static Answer<List<Optional<CategorizationResult>>> slowClaudeCall(Duration delay) {
        return invocation -> {
            Thread.sleep(delay.toMillis());
            List<String> texts = invocation.getArgument(0);
            return Collections.nCopies(texts.size(), Optional.of(new CategorizationResult(
                    Category.SONSTIGES, CategorizationResult.Source.CLAUDE)));
        };
    }

    /**
     * Die tragende Aussage: Die Laufzeit hängt an der Zahl der <em>Bündel</em>, nicht an der Zahl
     * der Transaktionen. Bei sequenzieller Abarbeitung wäre die Dauer mindestens
     * {@code count × Einzelverzögerung} — hier ist sie eine Grössenordnung darunter.
     */
    @Test
    void categorizationIsBatched_durationScalesWithBatchesNotTransactions() {
        int count = BATCH_SIZE * 2;
        Duration perCallDelay = Duration.ofMillis(150);
        when(importJobRepository.save(any(ImportJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categorizationPort.categorizeAll(any())).thenAnswer(slowClaudeCall(perCallDelay));

        Instant start = Instant.now();
        runner.run(new ImportJob(USER_ID, "sha-fixture", count, Instant.now()),
                unknownTransactions(count), SHA, false);
        Duration elapsed = Duration.between(start, Instant.now());

        // 2 Bündel × 150 ms ≈ 300 ms. Sequenziell wären es 40 × 150 ms = 6 s — die Grenze liegt
        // bewusst weit dazwischen, damit der Test die Aussage trägt, ohne an Jitter zu scheitern.
        assertThat(elapsed).isLessThan(perCallDelay.multipliedBy(count / 2));
        verify(categorizationPort, org.mockito.Mockito.times(2)).categorizeAll(any());
    }

    /**
     * Gegenprobe zur Bündelung: Der Port wird mit ganzen Listen aufgerufen, nicht mit
     * Einzeltexten. Ohne diese Assertion wäre die Dauer oben auch mit 40 parallelen Einzelcalls
     * erklärbar — und die hätte andere Konsequenzen (Rate-Limits, Circuit-Breaker-Semantik).
     */
    @Test
    void categorizationPortIsCalledWithWholeBatches() {
        when(importJobRepository.save(any(ImportJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categorizationPort.categorizeAll(any())).thenAnswer(slowClaudeCall(Duration.ZERO));

        runner.run(new ImportJob(USER_ID, "sha-fixture", BATCH_SIZE, Instant.now()),
                unknownTransactions(BATCH_SIZE), SHA, false);

        verify(categorizationPort).categorizeAll(
                org.mockito.ArgumentMatchers.argThat(texts -> texts.size() == BATCH_SIZE));
    }
}
