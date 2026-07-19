package com.budgetbuddy.transaction;

import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.Category;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestriert den PDF-Import-Flow (BE-PDF-02, US-04): SHA-256-Hash → Duplikatcheck →
 * PDFBox-Parse ({@link SwissBankStatementParser}) → Kategorisierung ({@link CategorizationPort},
 * via {@code @Primary} die Hybrid-Kette aus ADR-6) → Persistierung.
 *
 * <p><strong>Kein PDF in der DB:</strong> Von den PDF-Bytes wird ausschliesslich der SHA-256-Hash
 * gespeichert ({@code transactions.pdf_sha256}) — er dient als Duplikat-Schlüssel pro User.
 *
 * <p><strong>Timeout (kooperativ):</strong> Nach dem Parse sowie vor jedem Kategorisierungs-Call
 * wird die injizierte {@link Clock} gegen das Zeitbudget geprüft
 * ({@code budgetbuddy.import.timeout-seconds}, Default 30). Überschritten →
 * {@link PdfImportTimeoutException}; dank {@code @Transactional} wird nichts persistiert (kein
 * Partial-Import). Kooperativ heisst: Ein laufender Schritt wird nie abgebrochen, nur der nächste
 * verhindert — die reale Obergrenze ist damit Deadline + ein vollständiger Claude-Call
 * (10s SDK-Timeout × 2 Versuche, BE-CAT-02), bei Default 30s also ~50s. Ein präemptiver
 * Thread-Abbruch würde diese Lücke schliessen, wäre hier aber nur Komplexität ohne Zusatznutzen,
 * da SDK-Timeout und Circuit Breaker den Einzelcall bereits begrenzen.
 *
 * <p><strong>Jede Transaktion erhält eine Kategorie</strong> (AC BE-PDF-02): Liefert die
 * Kategorisierung {@link java.util.Optional#empty()} (leerer Text), fällt sie auf
 * {@link Category#SONSTIGES}.
 */
@Service
public class PdfImportService {

    private static final Logger log = LoggerFactory.getLogger(PdfImportService.class);

    private final SwissBankStatementParser parser;
    private final CategorizationPort categorizationPort;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final Duration timeout;

    public PdfImportService(
            SwissBankStatementParser parser,
            CategorizationPort categorizationPort,
            TransactionRepository transactionRepository,
            Clock clock,
            @Value("${budgetbuddy.import.timeout-seconds:30}") long timeoutSeconds) {
        this.parser = parser;
        this.categorizationPort = categorizationPort;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Importiert alle Transaktionen aus einem Kontoauszug-PDF für den angegebenen User.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param pdfBytes vollständiger Inhalt der PDF-Datei; wird nicht persistiert.
     * @return Hash und Anzahl der importierten Transaktionen (0, wenn das PDF keine enthält —
     *     Fehlerverhalten dafür ist BE-PDF-04).
     * @throws DuplicatePdfImportException wenn dieser User dasselbe PDF bereits importiert hat.
     * @throws PdfImportTimeoutException wenn das Zeitbudget überschritten wurde.
     * @throws PasswordProtectedPdfException wenn das PDF verschlüsselt ist.
     * @throws PdfParseException wenn das PDF nicht gelesen werden kann.
     */
    @Transactional
    public ImportResult importPdf(long userId, byte[] pdfBytes) {
        Instant deadline = clock.instant().plus(timeout);

        String pdfSha256 = sha256Hex(pdfBytes);
        if (transactionRepository.existsByUserIdAndPdfSha256(userId, pdfSha256)) {
            throw new DuplicatePdfImportException(pdfSha256);
        }

        List<ParsedTransaction> parsed = parser.parse(pdfBytes);
        // PDFBox kennt kein Timeout — ein pathologisches PDF kann den Parse beliebig lange
        // beschäftigen. Ohne diesen Check würde die Deadline erst greifen, wenn auch noch
        // kategorisiert wird (realistischster Ausfallpfad ganz ohne Claude-Beteiligung).
        if (clock.instant().isAfter(deadline)) {
            log.warn("PDF-Import für User {} nach dem Parsen abgebrochen (Timeout {}s).",
                    userId, timeout.toSeconds());
            throw new PdfImportTimeoutException(timeout);
        }

        List<Transaction> entities = new ArrayList<>(parsed.size());
        for (ParsedTransaction tx : parsed) {
            if (clock.instant().isAfter(deadline)) {
                log.warn("PDF-Import für User {} nach {} von {} Transaktionen abgebrochen "
                        + "(Timeout {}s).", userId, entities.size(), parsed.size(),
                        timeout.toSeconds());
                throw new PdfImportTimeoutException(timeout);
            }
            // fullText() = Buchungszeile + Detailzeilen (Empfänger) — der Input, mit dem beide
            // Stufen der Hybrid-Kategorisierung etwas anfangen können (ADR-6).
            String category = categorizationPort
                    .categorize(tx.fullText())
                    .orElse(Category.SONSTIGES)
                    .getLabel();
            entities.add(new Transaction(userId, tx.buchungsdatum(), tx.buchungstext(),
                    tx.betrag(), tx.isIncome(), category, pdfSha256));
        }

        transactionRepository.saveAll(entities);
        log.info("PDF-Import für User {}: {} Transaktion(en) importiert.", userId, entities.size());
        return new ImportResult(pdfSha256, entities.size());
    }

    private static String sha256Hex(byte[] pdfBytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdfBytes));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 ist in jeder JVM garantiert (Java SE Security-Spezifikation).
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
