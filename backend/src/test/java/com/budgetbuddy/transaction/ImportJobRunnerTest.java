package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit-Test des asynchronen Import-Teils (BE-PDF-09, ADR-13): Bündelweise Kategorisierung,
 * Fortschritt am {@link ImportJob}, Watchdog-Degradation und Persistierung.
 *
 * <p>Der {@code @Async}-Aspekt ist hier bewusst nicht im Spiel — die Methode wird direkt
 * aufgerufen. Getestet wird ihr Verhalten, nicht Springs Proxy; dass der Aufruf in Produktion
 * über den Proxy geht, sichert der Kontext-Test {@link PdfImportServiceIntegrationTest}.
 */
class ImportJobRunnerTest {

    private static final long USER_ID = 42L;
    private static final String SHA = "abc123";
    private static final Instant T0 = Instant.parse("2026-07-18T12:00:00Z");
    private static final long WATCHDOG_SECONDS = 300L;
    private static final int BATCH_SIZE = 2;

    private final CategorizationPort categorizationPort = mock(CategorizationPort.class);
    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final ImportJobRepository importJobRepository = mock(ImportJobRepository.class);
    private final Clock clock = mock(Clock.class);

    /**
     * Echtes {@link TransactionTemplate} über einem gemockten Transaktionsmanager: der Callback
     * wird tatsächlich ausgeführt (ein gemocktes Template täte gar nichts und liesse Delete und
     * saveAll unbeobachtet), Begin/Commit landen wirkungslos auf dem Mock.
     */
    private final TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    private final ImportJobRunner runner = new ImportJobRunner(categorizationPort, repository,
            importJobRepository, transactionTemplate, clock, WATCHDOG_SECONDS, BATCH_SIZE);

    @BeforeEach
    void persistJobsAsGiven() {
        when(importJobRepository.save(any(ImportJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ParsedTransaction parsed(String text, List<String> details, String betrag,
            boolean income) {
        return new ParsedTransaction(LocalDate.of(2026, 7, 1), text, details,
                new BigDecimal(betrag), income);
    }

    private static List<ParsedTransaction> unknownTransactions(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> parsed("Unbekannter Händler " + i, List.of(), "10.00", false))
                .toList();
    }

    /** Clock liefert für jeden instant()-Aufruf T0 — der Watchdog schlägt nie zu. */
    private void clockNeverExpires() {
        when(clock.instant()).thenReturn(T0);
    }

    private void categorizeAllAs(Category category, CategorizationResult.Source source) {
        when(categorizationPort.categorizeAll(any())).thenAnswer(invocation -> {
            List<String> texts = invocation.getArgument(0);
            return Collections.nCopies(
                    texts.size(), Optional.of(new CategorizationResult(category, source)));
        });
    }

    @SuppressWarnings("unchecked")
    private List<Transaction> capturePersisted() {
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void happyPath_persistsAllTransactionsWithCategoryAndHashAndFinishesTheJob() {
        clockNeverExpires();
        categorizeAllAs(Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP);
        ImportJob job = new ImportJob(USER_ID, "sha-fixture", 2, T0);

        runner.run(job, List.of(
                parsed("ESR", List.of("Stadtwerke Bern"), "78.50", false),
                parsed("GIRO POST", List.of(), "850.00", true)), SHA, false);

        List<Transaction> persisted = capturePersisted();
        assertThat(persisted).hasSize(2);
        assertThat(persisted).allSatisfy(tx -> {
            assertThat(tx.getUserId()).isEqualTo(USER_ID);
            assertThat(tx.getCategory()).isEqualTo(Category.LEBENSMITTEL.getLabel());
            assertThat(tx.getPdfSha256()).isEqualTo(SHA);
        });
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(job.getProcessed()).isEqualTo(2);
        assertThat(job.isDegraded()).isFalse();
        assertThat(job.getFinishedAt()).isEqualTo(T0);
    }

    /**
     * Der Kategorisierung wird der volle Text übergeben — Buchungszeile plus Detailzeilen. Der
     * Empfänger steht oft erst in den Details, und ohne ihn kann weder Lookup noch Claude etwas
     * zuordnen (ADR-6).
     */
    @Test
    void categorizationInput_isFullTextIncludingDetails() {
        clockNeverExpires();
        categorizeAllAs(Category.SONSTIGES, CategorizationResult.Source.CLAUDE);

        runner.run(new ImportJob(USER_ID, "sha-fixture", 1, T0),
                List.of(parsed("ESR", List.of("Stadtwerke Bern", "3000 Bern"), "78.50", false)),
                SHA, false);

        ArgumentCaptor<List<String>> texts = ArgumentCaptor.forClass(List.class);
        verify(categorizationPort).categorizeAll(texts.capture());
        assertThat(texts.getValue()).singleElement()
                .asString()
                .contains("ESR")
                .contains("Stadtwerke Bern")
                .contains("3000 Bern");
    }

    /** Jede Transaktion erhält eine Kategorie (AC BE-PDF-02) — auch die ohne Ergebnis. */
    @Test
    void emptyCategorization_fallsBackToSonstiges() {
        clockNeverExpires();
        when(categorizationPort.categorizeAll(any())).thenAnswer(invocation ->
                Collections.nCopies(((List<?>) invocation.getArgument(0)).size(), Optional.empty()));

        runner.run(new ImportJob(USER_ID, "sha-fixture", 1, T0),
                List.of(parsed("GIRO POST", List.of(), "850.00", false)), SHA, false);

        assertThat(capturePersisted()).singleElement()
                .extracting(Transaction::getCategory)
                .isEqualTo(Category.SONSTIGES.getLabel());
    }

    /**
     * <strong>AC4 aus #192</strong> — mehr Transaktionen, als in einem Zeitbudget verarbeitbar
     * sind.
     *
     * <p>Der alte Flow warf hier {@code PdfImportTimeoutException} und persistierte
     * <em>nichts</em>: 30 Sekunden Wartezeit, danach kein einziger importierter Posten (#192).
     * Neu wird nicht abgebrochen — die restlichen Transaktionen fallen ohne Claude-Call auf
     * {@code Sonstiges} und der Import ist vollständig gespeichert. Die tragende Assertion ist
     * deshalb die Anzahl persistierter Zeilen, nicht der Job-Status.
     */
    @Test
    void exceedingTheWatchdog_keepsTheWholeImportAndDegradesTheRestToSonstiges() {
        // instant(): 1. Start T0, 2. Check vor Bündel 1 (ok), 3. Check vor Bündel 2
        // (überschritten). Ab dann greift der degraded-Kurzschluss, es folgt nur noch der
        // Endzeitpunkt.
        Instant tooLate = T0.plusSeconds(WATCHDOG_SECONDS + 1);
        when(clock.instant()).thenReturn(T0, T0, tooLate);
        categorizeAllAs(Category.LEBENSMITTEL, CategorizationResult.Source.CLAUDE);
        ImportJob job = new ImportJob(USER_ID, "sha-fixture", 6, T0);

        runner.run(job, unknownTransactions(6), SHA, false);

        List<Transaction> persisted = capturePersisted();
        assertThat(persisted).hasSize(6);
        // Bündel 1 (BATCH_SIZE = 2) lief noch durch Claude, der Rest nicht.
        assertThat(persisted).extracting(Transaction::getCategory)
                .containsExactly(
                        Category.LEBENSMITTEL.getLabel(), Category.LEBENSMITTEL.getLabel(),
                        Category.SONSTIGES.getLabel(), Category.SONSTIGES.getLabel(),
                        Category.SONSTIGES.getLabel(), Category.SONSTIGES.getLabel());

        // Der Job ist erfolgreich abgeschlossen, nicht gescheitert: Der Nutzer hat seine Daten.
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(job.isDegraded()).isTrue();
        assertThat(job.getProcessed()).isEqualTo(6);

        // Nach der Überschreitung geht kein Request mehr hinaus — genau ein Bündel wurde gefragt.
        verify(categorizationPort).categorizeAll(any());
    }

    /**
     * Der Fortschritt wächst pro Bündel und wird sofort committet — das ist die Zahl, die der
     * nächste Status-Poll sieht. Ohne Zwischen-Save stünde der Balken bis zum Schluss auf 0.
     */
    @Test
    void progress_isPersistedAfterEveryBatch() {
        clockNeverExpires();
        categorizeAllAs(Category.SONSTIGES, CategorizationResult.Source.CLAUDE);
        ImportJob job = new ImportJob(USER_ID, "sha-fixture", 6, T0);

        List<Integer> progressAtEachSave = new ArrayList<>();
        when(importJobRepository.save(any(ImportJob.class))).thenAnswer(invocation -> {
            progressAtEachSave.add(((ImportJob) invocation.getArgument(0)).getProcessed());
            return invocation.getArgument(0);
        });

        runner.run(job, unknownTransactions(6), SHA, false);

        // 3 Bündel à 2 plus der abschliessende Save.
        assertThat(progressAtEachSave).containsExactly(2, 4, 6, 6);
    }

    @Test
    void forceImport_replacesThePreviousImportBeforeInserting() {
        clockNeverExpires();
        categorizeAllAs(Category.SONSTIGES, CategorizationResult.Source.CLAUDE);
        when(repository.deleteByUserIdAndPdfSha256(USER_ID, SHA)).thenReturn(3L);

        runner.run(new ImportJob(USER_ID, "sha-fixture", 1, T0),
                List.of(parsed("GIRO POST", List.of(), "850.00", false)), SHA, true);

        var order = org.mockito.Mockito.inOrder(repository);
        order.verify(repository).deleteByUserIdAndPdfSha256(USER_ID, SHA);
        order.verify(repository).saveAll(any());
    }

    @Test
    void regularImport_neverDeletesExistingTransactions() {
        clockNeverExpires();
        categorizeAllAs(Category.SONSTIGES, CategorizationResult.Source.CLAUDE);

        runner.run(new ImportJob(USER_ID, "sha-fixture", 1, T0),
                List.of(parsed("GIRO POST", List.of(), "850.00", false)), SHA, false);

        verify(repository, never()).deleteByUserIdAndPdfSha256(any(), anyString());
        verify(repository).saveAll(any());
    }

    /**
     * Ohne diesen Auffangpfad stürbe der Task still im Executor und der Job stünde für immer auf
     * {@code RUNNING} — das Frontend pollte dann endlos gegen einen Lauf, den es nicht mehr gibt.
     */
    @Test
    void unexpectedFailure_marksTheJobFailedInsteadOfLeavingItRunning() {
        clockNeverExpires();
        when(categorizationPort.categorizeAll(any()))
                .thenThrow(new IllegalStateException("kaputt"));
        ImportJob job = new ImportJob(USER_ID, "sha-fixture", 1, T0);

        runner.run(job, List.of(parsed("GIRO POST", List.of(), "850.00", false)), SHA, false);

        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.FAILED);
        verify(repository, never()).saveAll(any());
    }

    /** Nichts zu tun heisst: kein Request, kein Schreibzugriff — aber ein abgeschlossener Job. */
    @Test
    void noTransactions_finishesWithoutCallingCategorization() {
        clockNeverExpires();
        ImportJob job = new ImportJob(USER_ID, "sha-fixture", 0, T0);

        runner.run(job, List.of(), SHA, false);

        verifyNoInteractions(categorizationPort);
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.DONE);
    }
}
