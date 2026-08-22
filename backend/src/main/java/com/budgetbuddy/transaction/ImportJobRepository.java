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
}
