package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Empirischer Nachweis der in BE-PDF-08 untersuchten Timeout-Kette: die
 * Kategorisierungs-Schleife in {@link PdfImportService} ist strikt sequenziell (kein Batching,
 * keine Parallelität) und der Deadline-Check ist kooperativ, nicht präventiv — ein bereits
 * laufender Categorize-Call wird nicht abgebrochen, selbst wenn er das Budget sprengt.
 *
 * <p>Je Aussage genau eine tragende Assertion: die Sequenzialität belegt die Gesamtdauer in
 * {@code manyUnknownTransactions_categorizationIsSequentialNotBatched} (mindestens
 * {@code count × Einzelverzögerung}), die Kooperativität die Überschreitung des Budgets in
 * {@code manyUnknownTransactions_exceedingDeadlineMidLoop_abortsWithoutFinishingAll}. Der
 * Exception-Typ und die Call-Anzahl allein unterscheiden kooperativ und präventiv <em>nicht</em>:
 * beide wären auch bei einem Thread-Interrupt erfüllt.
 *
 * <p>Im Unterschied zu {@link PdfImportServiceTest} läuft hier eine echte {@link Clock}
 * (kein deterministisches Mock): erst damit misst {@code Thread.sleep} in der gefakten
 * {@link CategorizationPort} eine reale Wall-Clock-Dauer, statt gegen vorskriptete Instants zu
 * laufen. Verzögerungen sind bewusst klein gehalten (100–400 ms), damit der Test schnell bleibt,
 * ohne den Mechanismus zu verfälschen.
 */
class PdfImportServiceTimingTest {

    private static final long USER_ID = 42L;
    private static final byte[] PDF_BYTES = "fake-pdf-bytes".getBytes();

    private final SwissBankStatementParser parser = mock(SwissBankStatementParser.class);
    private final CategorizationPort categorizationPort = mock(CategorizationPort.class);
    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    private static List<ParsedTransaction> unknownTransactions(int count) {
        List<ParsedTransaction> transactions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            transactions.add(new ParsedTransaction(LocalDate.of(2026, 7, 1),
                    "Unbekannter Händler " + i, List.of(), new BigDecimal("10.00"), false));
        }
        return transactions;
    }

    /** Simuliert einen Claude-Call, der {@code delay} braucht, bevor er ein Ergebnis liefert. */
    private static Answer<Optional<CategorizationResult>> slowClaudeCall(Duration delay) {
        return invocation -> {
            Thread.sleep(delay.toMillis());
            return Optional.of(new CategorizationResult(
                    Category.SONSTIGES, CategorizationResult.Source.CLAUDE));
        };
    }

    @Test
    void manyUnknownTransactions_categorizationIsSequentialNotBatched() {
        int count = 5;
        Duration perCallDelay = Duration.ofMillis(100);
        PdfImportService service = new PdfImportService(parser, categorizationPort, repository,
                transactionTemplate, Clock.systemUTC(), 30L);
        when(repository.existsByUserIdAndPdfSha256(eq(USER_ID), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(unknownTransactions(count));
        when(categorizationPort.categorize(anyString())).thenAnswer(slowClaudeCall(perCallDelay));

        Instant start = Instant.now();
        service.importPdf(USER_ID, PDF_BYTES, false);
        Duration elapsed = Duration.between(start, Instant.now());

        // Bei Batching/Parallelität wäre die Dauer unabhängig von count; sequenziell muss sie
        // mindestens count-mal die Einzelverzögerung betragen (Toleranz nach unten: keine).
        assertThat(elapsed).isGreaterThanOrEqualTo(perCallDelay.multipliedBy(count));
    }

    @Test
    void manyUnknownTransactions_exceedingDeadlineMidLoop_abortsWithoutFinishingAll() {
        int count = 6;
        Duration perCallDelay = Duration.ofMillis(400);
        long timeoutSeconds = 1L;
        PdfImportService service = new PdfImportService(parser, categorizationPort, repository,
                transactionTemplate, Clock.systemUTC(), timeoutSeconds);
        when(repository.existsByUserIdAndPdfSha256(eq(USER_ID), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(unknownTransactions(count));
        when(categorizationPort.categorize(anyString())).thenAnswer(slowClaudeCall(perCallDelay));

        // count * perCallDelay (2.4s) sprengt das 1s-Budget deutlich — die Deadline wird
        // zwischen zwei Calls erkannt, aber der laufende Call selbst wird nie unterbrochen: das
        // ist exakt der in PdfImportService.java:33-37 dokumentierte kooperative Mechanismus.
        Instant start = Instant.now();
        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES, false))
                .isInstanceOf(PdfImportTimeoutException.class);
        Duration elapsed = Duration.between(start, Instant.now());

        // Der eigentliche Nachweis "kooperativ statt präventiv": ein präventiver Abbruch
        // (Thread-Interrupt im laufenden Call) käme *innerhalb* des Budgets zurück. Weil der
        // angefangene Call zu Ende gelassen wird, überschreitet die reale Dauer das Budget —
        // um bis zu einen vollen Call (~1.2s bei 1s Budget, also reichlich Luft, nicht flaky).
        assertThat(elapsed).isGreaterThan(Duration.ofSeconds(timeoutSeconds));

        // Keine exakte Anzahl fixiert (Thread-Scheduling-Jitter) — nur, dass der Import
        // abgebrochen wurde, bevor alle Transaktionen kategorisiert waren.
        verify(categorizationPort, atMost(count - 1)).categorize(anyString());
    }
}
