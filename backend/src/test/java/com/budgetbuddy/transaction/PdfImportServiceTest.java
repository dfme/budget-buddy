package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.task.TaskRejectedException;

/**
 * Unit-Test des <strong>synchronen</strong> Import-Teils (BE-PDF-02, seit ADR-13 zugeschnitten):
 * Duplikatcheck, Parse, Zeitbudget des Parsens und das Anlegen des {@link ImportJob}.
 *
 * <p>Kategorisierung und Persistierung liegen seit BE-PDF-09 im {@link ImportJobRunner} und sind
 * dort getestet ({@link ImportJobRunnerTest}) — hier wird nur noch geprüft, dass der Lauf mit den
 * richtigen Daten angestossen wird. Das echte Parsen deckt
 * {@link SwissBankStatementParserFixtureTest} ab, den End-to-End-Pfad
 * {@link PdfImportServiceIntegrationTest}.
 */
class PdfImportServiceTest {

    private static final long USER_ID = 42L;
    private static final byte[] PDF_BYTES = "fake-pdf-bytes".getBytes();
    private static final Instant T0 = Instant.parse("2026-07-18T12:00:00Z");
    private static final long TIMEOUT_SECONDS = 30L;

    private final SwissBankStatementParser parser = mock(SwissBankStatementParser.class);
    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final ImportJobRepository importJobRepository = mock(ImportJobRepository.class);
    private final ImportJobRunner importJobRunner = mock(ImportJobRunner.class);
    private final Clock clock = mock(Clock.class);

    private final PdfImportService service = new PdfImportService(
            parser, repository, importJobRepository, importJobRunner, clock, TIMEOUT_SECONDS);

    @BeforeEach
    void persistJobsAsGiven() {
        // save() gibt die Entity unverändert zurück (in Produktion mit gesetzter ID) — so bleibt
        // der Job im Test derselbe und Zustandsänderungen sind direkt prüfbar.
        when(importJobRepository.save(any(ImportJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static String expectedSha256() throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PDF_BYTES));
    }

    private static ParsedTransaction parsed(String text, List<String> details, String betrag,
            boolean income) {
        return new ParsedTransaction(LocalDate.of(2026, 7, 1), text, details,
                new BigDecimal(betrag), income);
    }

    /** Clock liefert für jeden instant()-Aufruf T0 — kein Timeout. */
    private void clockNeverExpires() {
        when(clock.instant()).thenReturn(T0);
    }

    @Test
    void happyPath_createsRunningJobAndHandsTheParsedTransactionsToTheRunner() throws Exception {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        List<ParsedTransaction> transactions = List.of(
                parsed("ESR", List.of("Stadtwerke Bern"), "78.50", false),
                parsed("GIRO POST", List.of(), "850.00", true));
        when(parser.parse(PDF_BYTES)).thenReturn(transactions);

        ImportJob job = service.startImport(USER_ID, PDF_BYTES, false);

        // Der Nenner der Fortschrittsanzeige steht schon hier fest — das Parsen ist durch.
        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.RUNNING);
        assertThat(job.getTotal()).isEqualTo(2);
        assertThat(job.getProcessed()).isZero();
        assertThat(job.getUserId()).isEqualTo(USER_ID);

        verify(importJobRunner).run(job, transactions, expectedSha256(), false);
    }

    /**
     * Der Upload darf nicht auf die Kategorisierung warten — sonst wäre der ganze Umbau
     * wirkungslos. Belegt über die Arbeitsteilung: Der Service persistiert selbst nichts, er
     * übergibt.
     */
    @Test
    void startImport_doesNotPersistTransactionsItself() {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));

        service.startImport(USER_ID, PDF_BYTES, false);

        verify(repository, never()).saveAll(any());
        verify(repository, never()).deleteByUserIdAndPdfSha256(any(), anyString());
    }

    @Test
    void duplicatePdf_throwsConflictWithoutParsingOrStartingAJob() throws Exception {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(USER_ID, expectedSha256())).thenReturn(true);

        assertThatThrownBy(() -> service.startImport(USER_ID, PDF_BYTES, false))
                .isInstanceOf(DuplicatePdfImportException.class);

        verifyNoInteractions(parser, importJobRunner);
        verify(importJobRepository, never()).save(any());
    }

    @Test
    void forceImport_skipsDuplicateCheckAndPassesTheFlagOn() throws Exception {
        clockNeverExpires();
        List<ParsedTransaction> transactions =
                List.of(parsed("GIRO POST", List.of(), "850.00", false));
        when(parser.parse(PDF_BYTES)).thenReturn(transactions);

        ImportJob job = service.startImport(USER_ID, PDF_BYTES, true);

        verify(repository, never()).existsByUserIdAndPdfSha256(any(), anyString());
        verify(importJobRunner).run(job, transactions, expectedSha256(), true);
    }

    /**
     * PDFBox kennt kein eigenes Timeout: Frisst der Parse das ganze Budget, muss der Request
     * direkt danach mit 408 abbrechen. Weil noch kein Job existiert, bleibt auch nichts halb
     * angefangen zurück, auf das ein Frontend pollen könnte.
     */
    @Test
    void timeoutDuringParse_throwsWithoutCreatingAJob() {
        // instant(): 1. Deadline-Basis T0, 2. Parse-Start T0, 3. Check nach Parse (überschritten).
        when(clock.instant()).thenReturn(T0, T0, T0.plusSeconds(TIMEOUT_SECONDS + 1));
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));

        assertThatThrownBy(() -> service.startImport(USER_ID, PDF_BYTES, false))
                .isInstanceOf(PdfImportTimeoutException.class);

        verifyNoInteractions(importJobRunner);
        verify(importJobRepository, never()).save(any());
    }

    /**
     * Erkannter Auszug ohne Buchungen (BE-PDF-05): Es gibt nichts zu kategorisieren. Der Job wird
     * sofort abgeschlossen, sonst wartete das Frontend auf einen Lauf, den es nicht gibt.
     */
    @Test
    void emptyStatement_finishesTheJobImmediatelyWithoutStartingARun() {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of());

        ImportJob job = service.startImport(USER_ID, PDF_BYTES, false);

        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.DONE);
        assertThat(job.getTotal()).isZero();
        assertThat(job.isDegraded()).isFalse();
        verifyNoInteractions(importJobRunner);
    }

    /**
     * Läuft der Executor über, wird der Upload nicht blockiert und es entsteht auch kein neuer
     * Fehlerstatus: Der Job endet auf {@code FAILED} und das Frontend zeigt dieselbe Meldung wie
     * bei jedem anderen Job-Fehler.
     */
    @Test
    void rejectedExecution_marksTheJobFailedInsteadOfFailingTheUpload() {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));
        doThrow(new TaskRejectedException("Queue voll"))
                .when(importJobRunner).run(any(), any(), anyString(), anyBoolean());

        ImportJob job = service.startImport(USER_ID, PDF_BYTES, false);

        assertThat(job.getStatus()).isEqualTo(ImportJobStatus.FAILED);
        assertThat(job.getFinishedAt()).isEqualTo(T0);
    }

    /**
     * Mandantentrennung: Die Statusabfrage muss auf {@code findByIdAndUserId} gehen. Ginge sie
     * über {@code findById}, liesse sich mit einer hochgezählten Job-ID der Importfortschritt
     * fremder Nutzer beobachten — Job-IDs sind fortlaufend.
     */
    @Test
    void findJob_isScopedToTheAuthenticatedUser() {
        ImportJob job = new ImportJob(USER_ID, 3, T0);
        when(importJobRepository.findByIdAndUserId(7L, USER_ID)).thenReturn(Optional.of(job));

        assertThat(service.findJob(USER_ID, 7L)).contains(job);
        assertThat(service.findJob(USER_ID + 1, 7L)).isEmpty();
    }

    /**
     * Die Zeitzone der Clock darf das Budget nicht beeinflussen — {@link Instant} ist
     * zonenunabhängig, und der Test hält das gegen eine spätere Umstellung auf
     * {@code LocalDateTime} fest.
     */
    @Test
    void usesFixedClockZone_isIrrelevantForBudget() {
        Clock fixed = Clock.fixed(T0, ZoneOffset.ofHours(12));
        PdfImportService fixedClockService = new PdfImportService(
                parser, repository, importJobRepository, importJobRunner, fixed, TIMEOUT_SECONDS);
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));

        assertThat(fixedClockService.startImport(USER_ID, PDF_BYTES, false).getTotal()).isEqualTo(1);
    }

    /** Der Hash im Job-Lauf ist der SHA-256 der PDF-Bytes — der Duplikat-Schlüssel pro User. */
    @Test
    void passesSha256OfPdfBytesToTheRunner() throws Exception {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));

        service.startImport(USER_ID, PDF_BYTES, false);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(importJobRunner).run(any(), any(), hash.capture(), eq(false));
        assertThat(hash.getValue()).isEqualTo(expectedSha256());
    }
}
