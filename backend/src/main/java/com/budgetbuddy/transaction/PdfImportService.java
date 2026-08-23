package com.budgetbuddy.transaction;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

/**
 * Synchroner Teil des PDF-Import-Flows (BE-PDF-02, US-04): SHA-256-Hash → Duplikatcheck →
 * PDFBox-Parse ({@link SwissBankStatementParser}) → {@link ImportJob} anlegen. Kategorisierung und
 * Persistierung übernimmt danach der {@link ImportJobRunner} im Hintergrund.
 *
 * <p><strong>Warum dieser Schnitt</strong> (ADR-13, BE-PDF-09): Das Parsen dauert ~2s, die
 * Kategorisierung ~28s (#192). Nur der lange Teil wandert in den Hintergrund. Der kurze bleibt im
 * Request, und damit bleiben auch alle Fehler, die er erzeugt, gewöhnliche HTTP-Fehler:
 * passwortgeschützt/gescannt/unbekanntes Layout → 400 mit {@code reason}, Duplikat → 409,
 * zu gross → 413. Ein Fehler, den der Nutzer sofort erfährt, ist besser als einer, den er sich
 * über einen Job-Status abholen muss.
 *
 * <p><strong>Kein PDF in der DB:</strong> Von den PDF-Bytes wird ausschliesslich der SHA-256-Hash
 * gespeichert ({@code transactions.pdf_sha256}) — er dient als Duplikat-Schlüssel pro User.
 *
 * <p><strong>Zeitbudget (kooperativ):</strong> Nach dem Parse wird die injizierte {@link Clock}
 * gegen {@code budgetbuddy.import.timeout-seconds} (Default 30) geprüft. Seit ADR-13 gilt dieses
 * Budget <em>nur noch fürs Parsen</em>: PDFBox kennt kein eigenes Timeout, ein pathologisches PDF
 * könnte den Request sonst beliebig lange binden. Überschritten →
 * {@link PdfImportTimeoutException} → 408, und weil noch kein Job existiert, ist auch nichts
 * halb angefangen. Für den Hintergrundlauf gilt ein eigener, weit grösserer Watchdog
 * ({@link ImportJobRunner}).
 *
 * <p><strong>Bewusst kein {@code @Transactional} um den Flow:</strong> Der Duplikatcheck liest
 * transaktionslos — der damit mögliche TOCTOU-Race bei parallelem Doppel-Upload bestand schon mit
 * Methoden-Transaktion (eine Transaktion allein sperrt die gelesenen Zeilen nicht) und ist als
 * Follow-up dokumentiert (eigene {@code pdf_imports}-Tabelle). Unter PostgreSQL ist er
 * wahrscheinlicher als vorher, weil parallele Writes nicht mehr wie in SQLite serialisiert werden
 * (DB-05, ADR-12).
 */
@Service
public class PdfImportService {

    private static final Logger log = LoggerFactory.getLogger(PdfImportService.class);

    private final SwissBankStatementParser parser;
    private final TransactionRepository transactionRepository;
    private final ImportJobRepository importJobRepository;
    private final ImportJobRunner importJobRunner;
    private final Clock clock;
    private final Duration parseTimeout;

    public PdfImportService(
            SwissBankStatementParser parser,
            TransactionRepository transactionRepository,
            ImportJobRepository importJobRepository,
            ImportJobRunner importJobRunner,
            Clock clock,
            @Value("${budgetbuddy.import.timeout-seconds:30}") long timeoutSeconds) {
        this.parser = parser;
        this.transactionRepository = transactionRepository;
        this.importJobRepository = importJobRepository;
        this.importJobRunner = importJobRunner;
        this.clock = clock;
        this.parseTimeout = Duration.ofSeconds(timeoutSeconds);
    }

    /**
     * Liest einen Kontoauszug ein und startet den Kategorisierungslauf.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @param pdfBytes vollständiger Inhalt der PDF-Datei; wird nicht persistiert.
     * @param force {@code true} überspringt den Duplikatcheck und <em>ersetzt</em> einen früheren
     *     Import desselben PDFs: dessen Transaktionen werden gelöscht, bevor die neuen geschrieben
     *     werden. Gedacht für die ausdrückliche Bestätigung «Trotzdem importieren» im
     *     Duplikat-Dialog (FE-PDF-03, US-04) — ohne diese Bestätigung entstehen keine Dubletten.
     *     Manuelle Kategorie-Korrekturen überleben das Ersetzen, weil sie als Lookup-Pattern in
     *     {@code category_lookup} liegen (ADR-6, Schritt 3) und beim Re-Import wieder greifen.
     * @return der angelegte Job mit der Anzahl erkannter Transaktionen als {@code total}. Bei 0
     *     erkannten Buchungen (Konto ohne Bewegung, BE-PDF-05) ist er bereits abgeschlossen —
     *     dann wird nichts persistiert und ein erneuter Upload desselben PDFs gilt nicht als
     *     Duplikat. Ein PDF ohne erkennbares Format wirft im Parser (BE-PDF-04).
     * @throws DuplicatePdfImportException wenn dieser User dasselbe PDF bereits importiert hat
     *     und {@code force} nicht gesetzt ist.
     * @throws PdfImportTimeoutException wenn das Parsen das Zeitbudget überschritten hat.
     * @throws PasswordProtectedPdfException wenn das PDF verschlüsselt ist.
     * @throws PdfParseException wenn das PDF nicht gelesen oder keine Transaktion extrahiert
     *     werden kann — inkl. der Subtypen {@link MissingTextLayerException} (Scan ohne
     *     Textlayer) und {@link UnsupportedStatementFormatException} (unbekanntes Layout).
     */
    public ImportJob startImport(long userId, byte[] pdfBytes, boolean force) {
        Instant deadline = clock.instant().plus(parseTimeout);

        String pdfSha256 = sha256Hex(pdfBytes);
        if (!force && isDuplicate(userId, pdfSha256)) {
            throw new DuplicatePdfImportException(pdfSha256);
        }

        // Phasendauer (BE-PDF-06): Der Parse ist CPU-gebunden und lokal, die Kategorisierung
        // netzgebunden — sie haben verschiedene Ursachen und Fixes und werden getrennt gemessen
        // (die zweite Hälfte loggt der ImportJobRunner). Zeitquelle ist bewusst die injizierte
        // Clock, nicht System.nanoTime(): nanoTime() wäre monoton und immun gegen NTP-Sprünge,
        // aber nicht über die Clock testbar.
        Instant parseStart = clock.instant();
        List<ParsedTransaction> parsed = parser.parse(pdfBytes);
        Instant parseEnd = clock.instant();
        if (parseEnd.isAfter(deadline)) {
            log.warn("PDF-Import für User {} nach dem Parsen abgebrochen (Timeout {}s).",
                    userId, parseTimeout.toSeconds());
            throw new PdfImportTimeoutException(parseTimeout);
        }
        log.info("PDF-Import für User {}: {} Transaktion(en) erkannt (Parse {} ms).",
                userId, parsed.size(), Duration.between(parseStart, parseEnd).toMillis());

        ImportJob job = importJobRepository.save(
                new ImportJob(userId, pdfSha256, parsed.size(), parseEnd));
        if (parsed.isEmpty()) {
            // Erkannter Auszug ohne Buchungen (BE-PDF-05): nichts zu kategorisieren, nichts zu
            // persistieren. Der Job wird sofort abgeschlossen, damit das Frontend nicht auf einen
            // Lauf wartet, den es nicht gibt.
            job.finishSuccessfully(false, parseEnd);
            return importJobRepository.save(job);
        }

        try {
            importJobRunner.run(job, parsed, pdfSha256, force);
        } catch (TaskRejectedException e) {
            // Pool und Queue voll. Statt den Upload-Request zu blockieren oder einen eigenen
            // Fehlerstatus zu erfinden, endet das im regulären Job-Fehlerpfad: Das Frontend
            // pollt ohnehin und zeigt dieselbe Meldung wie bei jedem anderen Job-Fehler.
            log.error("Import-Job {} für User {} konnte nicht gestartet werden (Executor voll).",
                    job.getId(), userId, e);
            job.fail(clock.instant());
            return importJobRepository.save(job);
        }
        return job;
    }

    /**
     * Liest den Stand eines Jobs für die Fortschrittsanzeige.
     *
     * <p>Die Einschränkung auf {@code userId} steht hier und nicht erst im Controller: Sie gehört
     * dorthin, wo die Query abgesetzt wird — sonst wäre sie von einem zweiten Aufrufer aus
     * umgehbar.
     *
     * @return der Job, wenn er diesem User gehört, sonst {@link Optional#empty()}.
     */
    public Optional<ImportJob> findJob(long userId, Long jobId) {
        return importJobRepository.findByIdAndUserId(jobId, userId);
    }

    /**
     * Wurde dieses PDF von diesem User schon importiert — oder wird es gerade?
     *
     * <p>Zwei Abfragen, weil der Hash an zwei Orten liegt und beide für sich unvollständig sind:
     *
     * <ul>
     *   <li>{@code transactions} trägt ihn erst, wenn der Hintergrundlauf fertig ist. Seit ADR-13
     *       liegt zwischen Upload und erstem geschriebenen Datensatz bis zu
     *       {@code categorization-timeout-seconds} plus ein vollständiges Bündel.
     *   <li>{@code import_jobs} trägt ihn ab dem Anlegen und deckt damit genau dieses Fenster ab.
     * </ul>
     *
     * <p>Ohne die zweite Abfrage genügt ein Reload während des Fortschrittsbalkens, um denselben
     * Auszug ein zweites Mal zu importieren: Die Upload-Komponente hält keine {@code jobId} und
     * nimmt nach dem Reload dieselbe Datei wieder an. Beide Jobs melden {@code DONE}, und
     * Safe-to-Spend ist still um den Faktor zwei falsch — gegen das ausdrückliche AC von US-04,
     * dass ohne Bestätigung keine Dubletten gespeichert werden. Vor ADR-13 gab es diesen Zustand
     * nicht: Ein Auszug dieser Grösse endete in 408 und schrieb gar nichts (#192).
     *
     * <p>Der schmale TOCTOU-Rest bleibt: Zwei Uploads, die sich zwischen Prüfung und Anlegen des
     * Jobs überholen, kommen weiterhin beide durch. Das Fenster ist damit aber wieder so kurz wie
     * vor der Umstellung — Millisekunden statt Minuten — und ist als eigenes Follow-up vermerkt.
     */
    private boolean isDuplicate(long userId, String pdfSha256) {
        return transactionRepository.existsByUserIdAndPdfSha256(userId, pdfSha256)
                || importJobRepository.existsByUserIdAndPdfSha256AndStatus(
                        userId, pdfSha256, ImportJobStatus.RUNNING);
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
