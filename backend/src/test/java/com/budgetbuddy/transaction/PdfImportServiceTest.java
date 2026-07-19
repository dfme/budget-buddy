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

import com.budgetbuddy.categorization.CategorizationPort;
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

    private final PdfImportService service = new PdfImportService(
            parser, categorizationPort, repository, clock, TIMEOUT_SECONDS);

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
                .thenReturn(Optional.of(Category.LEBENSMITTEL));
        when(categorizationPort.categorize("Saläreingang"))
                .thenReturn(Optional.of(Category.EINKOMMEN));

        ImportResult result = service.importPdf(USER_ID, PDF_BYTES);

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

    @Test
    void duplicatePdf_throwsConflictWithoutParsingOrCategorizing() throws Exception {
        when(clock.instant()).thenReturn(T0);
        when(repository.existsByUserIdAndPdfSha256(USER_ID, expectedSha256())).thenReturn(true);

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES))
                .isInstanceOf(DuplicatePdfImportException.class);

        verifyNoInteractions(parser, categorizationPort);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void timeoutDuringCategorization_throwsWithoutPersisting() {
        // instant(): 1. Deadline-Basis T0, 2. Check nach Parse (ok), 3. Check vor Tx1 (ok),
        // 4. Check vor Tx2 (überschritten).
        when(clock.instant()).thenReturn(T0, T0, T0, T0.plusSeconds(TIMEOUT_SECONDS + 1));
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("ESR", List.of("Stadtwerke Bern"), "78.50", false),
                parsed("GIRO POST", List.of(), "850.00", false)));
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(Category.SONSTIGES));

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES))
                .isInstanceOf(PdfImportTimeoutException.class);

        verify(repository, never()).saveAll(any());
    }

    @Test
    void timeoutDuringParse_throwsWithoutCategorizing() {
        // PDFBox kennt kein eigenes Timeout: Frisst der Parse das ganze Budget, muss der Import
        // direkt danach abbrechen — ohne einen einzigen Kategorisierungs-Call.
        // instant(): 1. Deadline-Basis T0, 2. Check nach Parse (überschritten).
        when(clock.instant()).thenReturn(T0, T0.plusSeconds(TIMEOUT_SECONDS + 1));
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES))
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

        service.importPdf(USER_ID, PDF_BYTES);

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
                .thenReturn(Optional.of(Category.WOHNEN));

        service.importPdf(USER_ID, PDF_BYTES);

        // Bei Überweisungen steht der Empfänger in den Detailzeilen — ohne ihn hätte die
        // Kategorisierung nur "ESR" als Input (ADR-6 liefe leer).
        verify(categorizationPort).categorize("ESR Stadtwerke Bern");
    }

    @Test
    void emptyParseResult_returnsZeroCountWithoutCategorizing() throws Exception {
        clockNeverExpires();
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of());

        ImportResult result = service.importPdf(USER_ID, PDF_BYTES);

        // 0 Transaktionen: kein Fehler auf Service-Ebene — Exception-Verhalten ist BE-PDF-04 (#83).
        assertThat(result.transactionCount()).isZero();
        assertThat(result.pdfSha256()).isEqualTo(expectedSha256());
        verifyNoInteractions(categorizationPort);
    }

    @Test
    void parserExceptions_propagateUnchanged() {
        when(clock.instant()).thenReturn(T0);
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenThrow(new PdfParseException("kaputt", null));

        assertThatThrownBy(() -> service.importPdf(USER_ID, PDF_BYTES))
                .isInstanceOf(PdfParseException.class);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void usesFixedClockZone_isIrrelevantForBudget() {
        // Regressionsschutz: Deadline-Arithmetik basiert auf Instant, nicht auf Zeitzonen.
        Clock fixed = Clock.fixed(T0, ZoneOffset.ofHours(12));
        PdfImportService fixedClockService = new PdfImportService(
                parser, categorizationPort, repository, fixed, TIMEOUT_SECONDS);
        when(repository.existsByUserIdAndPdfSha256(any(), anyString())).thenReturn(false);
        when(parser.parse(PDF_BYTES)).thenReturn(List.of(
                parsed("GIRO POST", List.of(), "850.00", false)));
        when(categorizationPort.categorize(anyString()))
                .thenReturn(Optional.of(Category.SONSTIGES));

        assertThat(fixedClockService.importPdf(USER_ID, PDF_BYTES).transactionCount()).isEqualTo(1);
    }
}
