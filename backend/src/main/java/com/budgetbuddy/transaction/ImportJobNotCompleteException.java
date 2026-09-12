package com.budgetbuddy.transaction;

/**
 * Der Import-Job existiert und gehört dem eingeloggten User, ist aber noch nicht abgeschlossen
 * (BE-PDF-14, US-04).
 *
 * <p>Wird von {@code GET /api/import/{jobId}/transactions} geworfen, solange der Job nicht
 * {@link ImportJobStatus#DONE} ist. Ein leeres oder halbes Ergebnis auszuliefern wäre die
 * schlechtere Antwort: Bei {@link ImportJobStatus#RUNNING} hat der {@link ImportJobRunner} noch
 * gar nichts geschrieben — er persistiert erst in seinem Abschlussblock —, und bei
 * {@link ImportJobStatus#FAILED} wird er es nie tun. Eine leere Liste wäre in beiden Fällen von
 * «dieser Auszug enthielt keine Buchungen» nicht zu unterscheiden.
 *
 * <p>Bewusst <strong>ohne</strong> {@code @ResponseStatus}: Die Abbildung auf 409 macht
 * {@link PdfImportExceptionHandler}, damit der Status als maschinenlesbarer Body mitgeht.
 * Begründung dort.
 */
public class ImportJobNotCompleteException extends RuntimeException {

    private final transient ImportJobStatus status;

    public ImportJobNotCompleteException(ImportJobStatus status) {
        super("Import-Job ist noch nicht abgeschlossen: " + status);
        this.status = status;
    }

    /** Der Stand, an dem der Job gerade steht — geht als Hinweis in den 409-Body. */
    public ImportJobStatus getStatus() {
        return status;
    }
}
