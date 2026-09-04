package com.budgetbuddy.transaction;

import com.budgetbuddy.categorization.CategorizationPort;
import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.config.AsyncConfig;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Asynchroner Teil des PDF-Imports (BE-PDF-09, ADR-14): kategorisiert die bereits geparsten
 * Transaktionen und persistiert sie.
 *
 * <p><strong>Warum eine eigene Klasse:</strong> {@code @Async} wirkt über einen Spring-Proxy. Ein
 * Aufruf innerhalb derselben Bean ginge am Proxy vorbei und liefe still synchron weiter — genau
 * der Fehler, den dieser Umbau beseitigen soll. Die Trennung von {@link PdfImportService} ist
 * deshalb nicht Geschmack, sondern Voraussetzung.
 *
 * <p><strong>Fortschritt:</strong> Nach jedem Bündel wächst {@code processed} am
 * {@link ImportJob} und wird sofort committet — das ist die Zahl, die
 * {@code GET /api/import/{jobId}/status} zurückgibt und der Fortschrittsbalken anzeigt. Die
 * Bündelgrösse bestimmt damit zugleich die Auflösung der Anzeige.
 *
 * <p><strong>Watchdog statt Zeitbudget:</strong> Auf diesen Lauf wartet kein HTTP-Request mehr,
 * die 30 Sekunden aus {@link PdfImportService} gelten nur noch fürs Parsen. Hier bremst nur
 * {@code budgetbuddy.import.categorization-timeout-seconds} einen hängenden Lauf. Läuft er
 * hinein, wird <strong>nicht</strong> abgebrochen: Die restlichen Transaktionen fallen ohne
 * Claude-Call auf {@link Category#SONSTIGES} und der Import wird vollständig gespeichert. Der
 * alte Zustand — 30 s warten und dann alle 108 Transaktionen verlieren (#192) — ist damit nicht
 * mehr erreichbar. Nicht kategorisierte Transaktionen kosten den Nutzer eine Korrektur, die nach
 * ADR-6 zugleich die Lookup-Tabelle füttert; ein verworfener Import kostet ihn alles.
 */
@Service
public class ImportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ImportJobRunner.class);

    private final CategorizationPort categorizationPort;
    private final TransactionRepository transactionRepository;
    private final ImportJobRepository importJobRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration categorizationTimeout;
    private final int batchSize;

    public ImportJobRunner(
            CategorizationPort categorizationPort,
            TransactionRepository transactionRepository,
            ImportJobRepository importJobRepository,
            TransactionTemplate transactionTemplate,
            Clock clock,
            @Value("${budgetbuddy.import.categorization-timeout-seconds:300}")
                    long categorizationTimeoutSeconds,
            @Value("${budgetbuddy.import.batch-size:20}") int batchSize) {
        this.categorizationPort = categorizationPort;
        this.transactionRepository = transactionRepository;
        this.importJobRepository = importJobRepository;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.categorizationTimeout = Duration.ofSeconds(categorizationTimeoutSeconds);
        this.batchSize = batchSize;
    }

    /**
     * Kategorisiert und persistiert die Transaktionen eines Imports.
     *
     * @param job der bereits angelegte, laufende Job — {@code total} steht fest, {@code processed}
     *     wächst hier.
     * @param parsed die geparsten Transaktionen in Reihenfolge des Auszugs.
     * @param pdfSha256 Duplikat-Schlüssel des Imports; landet an jeder Transaktion.
     * @param force {@code true} ersetzt einen früheren Import desselben PDFs (FE-PDF-03).
     */
    @Async(AsyncConfig.IMPORT_EXECUTOR)
    public void run(ImportJob job, List<ParsedTransaction> parsed, String pdfSha256, boolean force) {
        try {
            categorizeAndPersist(job, parsed, pdfSha256, force);
        } catch (RuntimeException e) {
            // Letzte Instanz: Ohne diesen Catch stürbe der Task still im Executor und der Job
            // stünde für immer auf RUNNING — das Frontend pollte dann endlos.
            markFailed(job, e);
        } catch (Error e) {
            // Ein Error lief bis BE-PDF-11 (#197) an diesem Catch vorbei und liess den Job genau
            // so stehen. Das ist hier kein exotischer Fall: Der Runner hält die geparsten
            // Transaktionen UND die aufgebauten Entities eines ganzen Auszugs im Speicher, bei
            // zwei Pool-Threads parallel — ein OutOfMemoryError ist auf einer Render-Starter-
            // Instanz erreichbar.
            //
            // Nach dem Markieren wird der Error weitergeworfen. Ihn zu schlucken hiesse, eine
            // beschädigte JVM als normalen Betrieb weiterlaufen zu lassen; wer den Job aufräumt,
            // erwirbt damit nicht das Recht, den Grund zu verschweigen.
            try {
                markFailed(job, e);
            } catch (RuntimeException whileFailing) {
                // Nach einem OutOfMemoryError kann schon dieser Schreibvorgang scheitern. Dann
                // bleibt der Job auf RUNNING und der StaleImportJobCleaner räumt ihn später ab —
                // aber der ursprüngliche Error darf dabei nicht verloren gehen.
                e.addSuppressed(whileFailing);
            }
            throw e;
        }
    }

    /**
     * Markiert einen abgebrochenen Job als {@link ImportJobStatus#FAILED}.
     *
     * <p>Die Logzeile ist bewusst offen formuliert statt "nichts persistiert": Für jeden Pfad bis
     * zum Persist-Block stimmt das, für einen Fehler danach (etwa im abschliessenden save) nicht —
     * dort stünde FAILED an einem Job, dessen Zeilen geschrieben sind. Eine Logzeile, die das
     * Gegenteil behauptet, schickt die Fehlersuche in die falsche Richtung.
     */
    private void markFailed(ImportJob job, Throwable cause) {
        log.error("Import-Job {} abgebrochen — Status FAILED. Ob Transaktionen "
                        + "geschrieben wurden, hängt davon ab, ob der Fehler vor oder nach "
                        + "dem Persistieren auftrat.",
                job.getId(), cause);
        job.fail(clock.instant());
        importJobRepository.save(job);
    }

    private void categorizeAndPersist(
            ImportJob job, List<ParsedTransaction> parsed, String pdfSha256, boolean force) {

        Instant start = clock.instant();
        Instant deadline = start.plus(categorizationTimeout);
        long userId = job.getUserId();

        // fullText() = Buchungszeile + Detailzeilen (Empfänger) — der Input, mit dem beide
        // Stufen der Hybrid-Kategorisierung etwas anfangen können (ADR-6).
        List<String> texts = parsed.stream().map(ParsedTransaction::fullText).toList();

        // Lookup-/Claude-Verhältnis (BE-PDF-06): die aussagekräftigste Einzelzahl des Flows —
        // ADR-6 rechnet mit 70–80% Lookup-Treffern, erst diese Zählung macht das überprüfbar.
        // «ohne Call» steht getrennt (Review PR #174): offener Circuit Breaker, fehlender
        // API-Key und seit ADR-14 auch der Watchdog liefern Sonstiges ohne HTTP-Request. Für die
        // ADR-6-Trefferquote zählt das wie Claude, für die Laufzeit nicht.
        int viaLookup = 0;
        int viaClaude = 0;
        int ohneCall = 0;
        boolean degraded = false;

        List<Transaction> entities = new ArrayList<>(parsed.size());
        for (int from = 0; from < parsed.size(); from += batchSize) {
            int to = Math.min(from + batchSize, parsed.size());

            // Der Check steht zwischen den Bündeln, nicht in ihnen: Ein laufender Call wird nie
            // unterbrochen. Die reale Obergrenze ist damit Deadline + ein vollständiges Bündel.
            if (!degraded && clock.instant().isAfter(deadline)) {
                degraded = true;
                log.warn("Import-Job {}: Zeitbudget von {}s nach {} von {} Transaktionen "
                                + "überschritten — der Rest wird ohne Claude-Call als '{}' "
                                + "gespeichert (der Import geht nicht verloren).",
                        job.getId(), categorizationTimeout.toSeconds(), from, parsed.size(),
                        Category.SONSTIGES.getLabel());
            }

            List<Optional<CategorizationResult>> batch = degraded
                    ? skipped(to - from)
                    : categorizationPort.categorizeAll(texts.subList(from, to));

            for (int position = 0; position < batch.size(); position++) {
                ParsedTransaction tx = parsed.get(from + position);
                CategorizationResult result = batch.get(position).orElse(null);
                // Jede Transaktion erhält eine Kategorie (AC BE-PDF-02): Liefert die
                // Kategorisierung Optional.empty() (leerer Text), fällt sie auf Sonstiges.
                String category = result == null
                        ? Category.SONSTIGES.getLabel()
                        : result.category().getLabel();
                if (result != null) {
                    switch (result.source()) {
                        case LOOKUP -> viaLookup++;
                        case CLAUDE -> viaClaude++;
                        case CLAUDE_SKIPPED -> ohneCall++;
                    }
                }
                // detailsAsText() statt fullText(): Der Buchungstext hat seine eigene Spalte,
                // die Detailzeilen ihre. Sie zusammenzuschreiben wäre irreversibel — genau das,
                // was ParsedTransaction für US-08 ausschliesst (BE-PDF-07).
                entities.add(new Transaction(userId, tx.buchungsdatum(), tx.buchungstext(),
                        tx.detailsAsText(), tx.betrag(), tx.isIncome(), category, pdfSha256));
            }

            // Sofort committen: Das ist die Zahl, die der nächste Status-Poll sehen soll.
            job.advance(to - from);
            importJobRepository.save(job);
        }

        // Ersetzen statt Anhängen: Delete und Insert in einer Transaktion, damit ein Fehler
        // dazwischen nicht die alten Zeilen ersatzlos entfernt. Die abgeleitete Delete-Query
        // braucht ohnehin eine laufende Transaktion — SimpleJpaRepository deckt nur seine
        // eigenen CRUD-Methoden ab, nicht deleteBy…-Ableitungen.
        transactionTemplate.executeWithoutResult(status -> {
            if (force) {
                long removed = transactionRepository.deleteByUserIdAndPdfSha256(userId, pdfSha256);
                log.info("Force-Import: {} Transaktion(en) des vorherigen Imports ersetzt.",
                        removed);
            }
            transactionRepository.saveAll(entities);
        });

        Instant end = clock.instant();
        job.finishSuccessfully(degraded, end);
        importJobRepository.save(job);

        // Eine Summary-Zeile pro Import (BE-PDF-06) — bewusst keine Zeile pro Transaktion,
        // application-prod.properties fährt com.budgetbuddy=INFO. Anders als vor ADR-14 steht sie
        // nicht mehr hinter einem Pfad, der im Fehlerfall übersprungen wird: Der Watchdog-Fall
        // endet ebenfalls hier und ist an `degraded` erkennbar (#192, Nebenbefund «Instrumen-
        // tierung ist im Fehlerfall blind»).
        log.info("Import-Job {}: {} Transaktion(en) importiert (Kategorisierung {} ms; "
                        + "{} via Lookup, {} via Claude, {} ohne Call{}).",
                job.getId(), entities.size(), Duration.between(start, end).toMillis(),
                viaLookup, viaClaude, ohneCall, degraded ? ", Zeitbudget überschritten" : "");
    }

    /** Ein Bündel, das ohne Claude-Call auskommt — {@code Sonstiges} über {@code CLAUDE_SKIPPED}. */
    private static List<Optional<CategorizationResult>> skipped(int size) {
        return Collections.nCopies(size, Optional.of(new CategorizationResult(
                Category.SONSTIGES, CategorizationResult.Source.CLAUDE_SKIPPED)));
    }
}
