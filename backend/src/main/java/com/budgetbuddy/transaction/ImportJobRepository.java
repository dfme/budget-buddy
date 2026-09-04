package com.budgetbuddy.transaction;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Jobs, die noch auf einem Status stehen, aber älter sind als die Schranke — die Grundlage der
     * Bereinigung verwaister Läufe (BE-PDF-11, siehe {@link StaleImportJobCleaner}).
     *
     * <p><strong>Bewusst ohne User-Einschränkung</strong>, als einzige Methode dieses Repositories.
     * Das ist keine Lücke in der Mandantentrennung, sondern deren Gegenstück: Diese Query steht auf
     * keinem Request-Pfad. Aufgerufen wird sie ausschliesslich vom {@link StaleImportJobCleaner},
     * der beim Start und danach periodisch läuft — dort gibt es keinen authentifizierten User, den
     * man einsetzen könnte, und die aufzuräumenden Jobs gehören per Definition beliebigen Usern.
     * Nach aussen dringt nichts: Der Cleaner gibt nur eine Anzahl zurück und loggt nur Job-IDs.
     *
     * <p>Wer diese Methode je aus einem Controller oder Service des Request-Pfads aufruft, baut
     * damit ein IDOR — dann gehört stattdessen eine Variante mit {@code AndUserId} hierher.
     *
     * @param status gesuchter Status — in der Bereinigung {@link ImportJobStatus#RUNNING}.
     * @param createdBefore Schranke; nur ältere Jobs kommen zurück (echt kleiner).
     * @return die betroffenen Jobs, in unbestimmter Reihenfolge.
     */
    List<ImportJob> findByStatusAndCreatedAtBefore(ImportJobStatus status, Instant createdBefore);

    /**
     * Löscht alle Import-Jobs eines Users (Kontolöschung, US-02, DB-07).
     *
     * <p>Bewusst {@code @Modifying} — Begründung wie bei
     * {@code TransactionRepository#deleteAllByUserId}: das DELETE muss physisch ausgeführt sein,
     * bevor {@code UserService.deleteUser} den User selbst löscht.
     */
    @Modifying
    @Query("delete from ImportJob j where j.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
