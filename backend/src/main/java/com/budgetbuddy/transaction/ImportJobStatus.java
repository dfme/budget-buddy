package com.budgetbuddy.transaction;

/**
 * Lebenszyklus eines {@link ImportJob} (BE-PDF-09, ADR-14).
 *
 * <p>Bewusst kurz: Das Parsen ist beim Anlegen des Jobs bereits durch — ein Job existiert erst,
 * wenn es etwas zu kategorisieren gibt. Alle Fehler des Parsens beantwortet der Upload-Request
 * weiterhin selbst mit 400/409/413.
 */
public enum ImportJobStatus {
    /** Kategorisierung läuft; {@code processed} wächst. */
    RUNNING,

    /**
     * Transaktionen sind persistiert. Auch der Watchdog-Fall endet hier — dann zusätzlich mit
     * {@code degraded = true}, denn der Import ist vollständig gespeichert und nur ein Teil ohne
     * Claude kategorisiert.
     */
    DONE,

    /**
     * Unerwarteter Fehler; nichts persistiert. Kein regulärer Ausgang: Claude-Ausfälle fallen auf
     * {@code Sonstiges} zurück und führen zu {@link #DONE}.
     *
     * <p>Seit BE-PDF-11 endet hier auch der Job, dessen Prozess gestorben ist, bevor er selbst
     * etwas melden konnte — gesetzt nachträglich durch den {@link StaleImportJobCleaner}. Für den
     * Nutzer ist das derselbe Ausgang und dieselbe Wahrheit: Der {@link ImportJobRunner} schreibt
     * die Transaktionen erst in seinem Abschlussblock, ein vorher abgebrochener Lauf hat nichts
     * hinterlassen. Woran der Job gescheitert ist, unterscheidet nur das Log — dafür gibt es
     * bewusst keine eigene Spalte (#197).
     */
    FAILED
}
