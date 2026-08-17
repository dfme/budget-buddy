package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Unit-Test der Import-Orchestrierung (BE-PDF-02): Duplikatcheck, Kategorisierung, Timeout und
 * Persistierung. Parser, Kategorisierung und Repository sind gemockt; das echte Parsen ist in
 * {@link SwissBankStatementParserFixtureTest} abgedeckt, der End-to-End-Pfad im
 * {@link PdfImportServiceIntegrationTest}.
 */
class PdfImportServiceTest {

    private static final long USER_ID = 42L;
    private static final byte[] PDF_BYTES = "fake-pdf-bytes".getBytes();
    private static final Instant T0 = Instant.parse("2026-07-18T12:00:00Z");
    private static final long TIMEOUT_SECONDS = 30L;

    private final SwissBankStatementParser parser = mock(SwissBankStatementParser.class);
    private final CategorizationPort categorizationPort = mock(CategorizationPort.class);
    private final TransactionRepository repository = mock(TransactionRepository.class);
    private final Clock clock = mock(Clock.class);

    /**
     * Echtes {@link TransactionTemplate} über einem gemockten Transaktionsmanager: der Callback
     * wird tatsächlich ausgeführt (ein gemocktes Template täte gar nichts und liesse Delete und
     * saveAll unbeobachtet), Begin/Commit landen wirkungslos auf dem Mock.
     */
    private final TransactionTemplate transactionTemplate =
            new TransactionTemplate(mock(PlatformTransactionManager.class));

    private final PdfImportService service = new PdfImportService(
            parser, categorizationPort, repository, transactionTemplate, clock, TIMEOUT_SECONDS);

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
    void happyPath_persistsAllTransactionsWithCategoryAndHash() throws Exception {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(USER_ID, expectedSha256())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("Kartenzahlung Migros Zuerich", List.of(), "87.60", false),
                parsed("Saläreingang", List.of(), "6800.00", true)));
        when(categorizationPort.categorize("Kartenzahlung Migros Zuerich"))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP)));
        when(categorizationPort.categorize("Saläreingang"))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.EINKOMMEN, CategorizationResult.Source.LOOKUP)));

        ImportResult result = service.importPdf(USER_ID, PDF_BYTES, false);

        assertThat(result.pdfSha256()).isEqualTo(expectedSha256());
        assertThat(result.transactionCount()).isEqualTo(2);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue().getFirst()).satisfies(tx -> {
            assertThat(tx.getUserId()).isEqualTo(USER_ID);
            assertThat(tx.getBuchungstext()).isEqualTo("Kartenzahlung Migros Zuerich");
            assertThat(tx.getBetrag()).isEqualByComparingTo("87.60");
            assertThat(tx.isIncome()).isFalse();
            assertThat(tx.getCategory()).isEqualTo("Lebensmittel");
            assertThat(tx.getPdfSha256()).isEqualTo(expectedSha256());
        });
        assertThat(saved.getValue().getLast().getCategory()).isEqualTo("Einkommen");
    }

    /**
     * BE-PDF-06: Eine Summary-Zeile pro Import auf INFO — mit getrennten Phasendauern (Parse vs.
     * Kategorisierung) und dem Lookup-/Claude-Verhältnis. Die Dauern sind über die gemockte Clock
     * deterministisch: Parse 2s, Kategorisierung 5s.
     *
     * <p>«ohne Call» steht als eigene Zahl neben «via Claude» (Review PR #174): Ein offener Circuit
     * Breaker liefert {@code Sonstiges} ohne HTTP-Request und damit ohne Latenz — als «via Claude»
     * gezählt läse sich die Zeile neben der Kategorisierungsdauer widersprüchlich.
     */
    @Test
    void happyPath_logsSummaryWithPhaseDurationsAndSourceRatio() throws Exception {
        // instant(): 1. Deadline-Basis T0, 2. Parse-Start T0, 3. Parse-Ende T0+2s,
        // 4.-7. Checks vor Tx1-Tx4 (ok), 8. Kategorisierungs-Ende T0+7s.
        when(clock.instant()).thenReturn(T0, T0, T0.plusSeconds(2),
                T0.plusSeconds(2), T0.plusSeconds(3), T0.plusSeconds(4), T0.plusSeconds(5),
                T0.plusSeconds(7));
        when(repository.existsByUserIdAndPdfSha256(USER_ID, expectedSha256())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("Kartenzahlung Migros Zuerich", List.of(), "87.60", false),
                parsed("Saläreingang", List.of(), "6800.00", true),
                parsed("KLEINGARTENVEREIN BEITRAG", List.of(), "120.00", false),
                parsed("UNBEKANNTER HAENDLER GMBH", List.of(), "42.00", false)));
        when(categorizationPort.categorize("Kartenzahlung Migros Zuerich"))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP)));
        when(categorizationPort.categorize("Saläreingang"))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.EINKOMMEN, CategorizationResult.Source.LOOKUP)));
        when(categorizationPort.categorize("KLEINGARTENVEREIN BEITRAG"))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.FREIZEIT, CategorizationResult.Source.CLAUDE)));
        // Breaker offen / kein API-Key: Sonstiges, aber ohne Request.
        when(categorizationPort.categorize("UNBEKANNTER HAENDLER GMBH"))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.SONSTIGES, CategorizationResult.Source.CLAUDE_SKIPPED)));

        Logger serviceLogger = (Logger) LoggerFactory.getLogger(PdfImportService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        serviceLogger.addAppender(appender);
        try {
            service.importPdf(USER_ID, PDF_BYTES, false);
        } finally {
            serviceLogger.detachAppender(appender);
        }

        assertThat(appender.list)
                .filteredOn(event -> event.getLevel() == Level.INFO)
                .singleElement()
                .extracting(ILoggingEvent::getFormattedMessage)
                .asString()
                .contains("User 42", "4 Transaktion(en)")
                .contains("Parse 2000 ms", "Kategorisierung 5000 ms")
                .contains("2 via Lookup", "1 via Claude", "1 ohne Call");
    }

    @Test
    void duplicatePdf_throwsConflictWithoutParsingOrCategorizing() throws Exception {
        when(clock.instant()).thenReturn(T0);
        when(repository.existsByUserIdAndPdfSha256(USER_ID, expectedSha256())).thenReturn(true);

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES, false))
                .isInstanceOf(DuplicatePdfImportException.class);

        verifyNoInteractions(parser, categorizationPort);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void forceImport_skipsDuplicateCheckAndReplacesPreviousImport() throws Exception {
        clockNeverExpires();
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("Kartenzahlung Migros Zuerich", List.of(), "87.60", false)));
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.LEBENSMITTEL, CategorizationResult.Source.LOOKUP)));

        ImportResult result = service.importPdf(USER_ID, PDF_BYTES, true);

        assertThat(result.transactionCount()).isEqualTo(1);
        // Der Check entfällt komplett — der User hat das Duplikat bereits bestätigt.
        verify(repository, never()).existsByUserIdAndPdfSha256(any(), anyString());
        // Ersetzen statt Anhängen: der frühere Import desselben PDFs verschwindet zuerst,
        // eingeschränkt auf diesen User (Mandantentrennung).
        verify(repository).deleteByUserIdAndPdfSha256(USER_ID, expectedSha256());
        verify(repository).saveAll(any());
    }

    @Test
    void regularImport_neverDeletesExistingTransactions() {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.SONSTIGES, CategorizationResult.Source.LOOKUP)));

        service.importPdf(USER_ID, PDF_BYTES, false);

        verify(repository, never()).deleteByUserIdAndPdfSha256(any(), anyString());
        verify(repository).saveAll(any());
    }

    @Test
    void forceImport_stillAbortsOnTimeoutWithoutTouchingExistingTransactions() {
        // instant(): 1. Deadline-Basis T0, 2. Parse-Start T0, 3. Check nach Parse (überschritten).
        // Der Force-Pfad darf nicht früher löschen als der reguläre speichert — sonst stünde der
        // User nach einem Timeout ohne seine alten Buchungen da.
        when(clock.instant()).thenReturn(T0, T0, T0.plusSeconds(TIMEOUT_SECONDS + 1));
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES, true))
                .isInstanceOf(PdfImportTimeoutException.class);

        verify(repository, never()).deleteByUserIdAndPdfSha256(any(), anyString());
        verify(repository, never()).saveAll(any());
    }

    @Test
    void timeoutDuringCategorization_throwsWithoutPersisting() {
        // instant(): 1. Deadline-Basis T0, 2. Parse-Start T0, 3. Check nach Parse (ok),
        // 4. Check vor Tx1 (ok), 5. Check vor Tx2 (überschritten).
        when(clock.instant()).thenReturn(T0, T0, T0, T0, T0.plusSeconds(TIMEOUT_SECONDS + 1));
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("ESR", List.of("Stadtwerke Bern"), "78.50", false),
                parsed("GIRO POST", List.of(), "850.00", false)));
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.SONSTIGES, CategorizationResult.Source.LOOKUP)));

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES, false))
                .isInstanceOf(PdfImportTimeoutException.class);

        verify(repository, never()).saveAll(any());
    }

    @Test
    void timeoutDuringParse_throwsWithoutCategorizing() {
        // PDFBox kennt kein eigenes Timeout: Frisst der Parse das ganze Budget, muss der Import
        // direkt danach abbrechen — ohne einen einzigen Kategorisierungs-Call.
        // instant(): 1. Deadline-Basis T0, 2. Parse-Start T0, 3. Check nach Parse (überschritten).
        when(clock.instant()).thenReturn(T0, T0, T0.plusSeconds(TIMEOUT_SECONDS + 1));
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES, false))
                .isInstanceOf(PdfImportTimeoutException.class);

        verifyNoInteractions(categorizationPort);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void emptyCategorization_fallsBackToSonstiges() {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));
        when(categorizationPort.categorize(anyString())).thenReturn(Optional.empty());

        service.importPdf(USER_ID, PDF_BYTES, false);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue().getFirst().getCategory()).isEqualTo("Sonstiges");
    }

    @Test
    void categorizationInput_isFullTextIncludingDetails() {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("ESR", List.of("Stadtwerke Bern"), "78.50", false)));
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.WOHNEN, CategorizationResult.Source.LOOKUP)));

        service.importPdf(USER_ID, PDF_BYTES, false);

        // Bei Überweisungen steht der Empfänger in den Detailzeilen — ohne ihn hätte die
        // Kategorisierung nur "ESR" als Input (ADR-6 liefe leer).
        verify(categorizationPort).categorize("ESR Stadtwerke Bern");
    }

    @Test
    void parserExceptions_propagateUnchanged() {
        when(clock.instant()).thenReturn(T0);
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenThrow(new PdfParseException("kaputt", null));

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES, false))
                .isInstanceOf(PdfParseException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void usesFixedClockZone_isIrrelevantForBudget() {
        // Regressionsschutz: Deadline-Arithmetik basiert auf Instant, nicht auf Zeitzonen.
        Clock fixed = Clock.fixed(T0, ZoneOffset.ofHours(12));
        PdfImportService fixedClockService = new PdfImportService(
                parser, categorizationPort, repository, transactionTemplate, fixed,
                TIMEOUT_SECONDS);
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(new CategorizationResult(
                        Category.SONSTIGES, CategorizationResult.Source.LOOKUP)));

        assertThat(fixedClockService.importPdf(USER_ID, PDF_BYTES, false).transactionCount())
                .isEqualTo(1);
    }
}
