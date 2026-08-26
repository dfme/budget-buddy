package com.budgetbuddy.transaction;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository der {@code import_jobs}-Tabelle (BE-PDF-09).
 *
 * <p>Es gibt bewusst <strong>kein</strong> {@code findById} im lesenden Pfad: Job-IDs sind
 * fortlaufend und damit ratbar. {@link #findByIdAndUserId} bindet jede Abfrage an den
 * authentifizierten User — ein fremder Job ist nicht «verboten», sondern nicht vorhanden.
 */
public interface ImportJobRepository extends JpaRepository<ImportJob, Long> {

    /**
     * @param id Job-ID aus der Upload-Antwort.
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return der Job, wenn er diesem User gehört — sonst {@link Optional#empty()}.
     */
    Optional<ImportJob> findByIdAndUserId(Long id, Long userId);

    /**
     * Läuft für diesen User bereits ein Import derselben Datei?
     *
     * <p>Zweite Hälfte des Duplikatchecks. Die erste fragt {@code transactions} — dort steht der
     * Hash aber erst, wenn der Hintergrundlauf fertig ist. Ohne diese Abfrage wäre der Check für
     * die gesamte Dauer des Laufs blind und derselbe Auszug landete doppelt in der Datenbank
     * (siehe {@code PdfImportService.startImport}).
     *
     * @param userId ID des besitzenden Users.
     * @param pdfSha256 SHA-256 des hochgeladenen PDFs.
     * @param status gesuchter Status — im Duplikatcheck {@link ImportJobStatus#RUNNING}.
     * @return {@code true}, wenn ein solcher Job existiert.
     */
    boolean existsByUserIdAndPdfSha256AndStatus(
            Long userId, String pdfSha256, ImportJobStatus status);
}
