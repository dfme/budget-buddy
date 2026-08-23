package com.budgetbuddy.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * JPA-Entity der {@code import_jobs}-Tabelle (Flyway V05, BE-PDF-09 / ADR-13).
 *
 * <p>Ein Job je Upload, angelegt <em>nach</em> dem synchronen Parsen: {@code total} steht damit
 * von Anfang an fest und die Fortschrittsanzeige kennt ihren Nenner beim ersten Poll.
 * {@code processed} wächst pro abgeschlossenem Kategorisierungs-Bündel.
 *
 * <p>{@code userId} ist nicht bloss Metadatum, sondern der Zugriffsschlüssel: Die Statusabfrage
 * liest ausschliesslich über {@link ImportJobRepository#findByIdAndUserId} — eine fremde Job-ID
 * darf nicht verraten, dass jemand anders gerade importiert.
 */
@Entity
@Table(name = "import_jobs")
public class ImportJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * SHA-256 des importierten PDFs — dieselbe Grösse, die an jeder Transaktion hängt.
     *
     * <p>Am Job mitgeführt, damit der Duplikatcheck beim Upload einen <em>laufenden</em> Import
     * derselben Datei sieht. In {@code transactions} steht der Hash erst nach dem Abschluss;
     * bis dahin wäre der Check blind (siehe {@code PdfImportService.startImport}).
     */
    @Column(name = "pdf_sha256", nullable = false)
    private String pdfSha256;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ImportJobStatus status;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int processed;

    @Column(nullable = false)
    private boolean degraded;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ImportJob() {
        // JPA
    }

    /**
     * Legt einen laufenden Job an.
     *
     * @param userId ID des besitzenden Users (aus dem JWT).
     * @param pdfSha256 SHA-256 des importierten PDFs — Grundlage des Duplikatchecks während des
     *     laufenden Imports.
     * @param total Anzahl geparster Transaktionen — der Nenner der Fortschrittsanzeige.
     * @param createdAt Anlagezeitpunkt aus der injizierten {@code Clock}.
     */
    public ImportJob(Long userId, String pdfSha256, int total, Instant createdAt) {
        this.userId = userId;
        this.pdfSha256 = pdfSha256;
        this.total = total;
        this.processed = 0;
        this.degraded = false;
        this.status = ImportJobStatus.RUNNING;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPdfSha256() {
        return pdfSha256;
    }

    public ImportJobStatus getStatus() {
        return status;
    }

    public int getTotal() {
        return total;
    }

    public int getProcessed() {
        return processed;
    }

    public boolean isDegraded() {
        return degraded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    /** Meldet ein abgeschlossenes Bündel; {@code processed} überschreitet nie {@code total}. */
    public void advance(int categorized) {
        this.processed = Math.min(this.total, this.processed + categorized);
    }

    /**
     * Schliesst den Job ab.
     *
     * @param degraded {@code true}, wenn der Watchdog zugeschlagen hat und ein Teil ohne
     *     Claude-Call auf {@code Sonstiges} gefallen ist. Die Transaktionen sind auch dann
     *     vollständig persistiert.
     */
    public void finishSuccessfully(boolean degraded, Instant finishedAt) {
        this.status = ImportJobStatus.DONE;
        this.processed = this.total;
        this.degraded = degraded;
        this.finishedAt = finishedAt;
    }

    /** Bricht den Job ab; es wurde nichts persistiert. */
    public void fail(Instant finishedAt) {
        this.status = ImportJobStatus.FAILED;
        this.finishedAt = finishedAt;
    }
}
