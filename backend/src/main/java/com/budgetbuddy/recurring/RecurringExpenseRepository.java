package com.budgetbuddy.recurring;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository-Zugriff auf {@link RecurringExpense} (recurring-intern, kein modulübergreifender
 * Zugriff — andere Module gehen über {@link RecurringExpenseDetectionPort} und
 * {@link RecurringExpenseCleanupPort}).
 */
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    /**
     * Alle Einträge eines Users, beide Status. Die Erkennung braucht beide: {@code DETECTED}, um
     * keine zweite Notification zu erzeugen, {@code DISMISSED}, um den Empfänger auszuschliessen.
     * Die Einschränkung auf {@code userId} ist die Mandantentrennung; der Index kommt aus der
     * {@code UNIQUE (user_id, payee_key)}-Constraint (V11).
     */
    List<RecurringExpense> findByUserId(Long userId);

    /**
     * Einträge eines Users in einem Status — für {@code list} (BE-REC-02): nur {@code DETECTED}
     * gehört in die Abo-Übersicht, ein «Kein Abo» markierter Eintrag verschwindet daraus (US-08).
     *
     * <p>Alphabetisch nach {@code payee_key}, damit die Übersicht zwischen zwei Aufrufen nicht
     * springt — ohne {@code ORDER BY} hinge die Reihenfolge an der Datenbank. Der Schlüssel ist pro
     * User eindeutig (V11), die Sortierung damit total.
     */
    List<RecurringExpense> findByUserIdAndStatusOrderByPayeeKeyAsc(
            Long userId, RecurringExpenseStatus status);

    /**
     * Einzelner Eintrag eines Users — für {@code dismiss} (BE-REC-02).
     *
     * @return leer, wenn die ID nicht existiert <em>oder</em> einem anderen User gehört. Beide
     *     Fälle bleiben für den Aufrufer bewusst ununterscheidbar (siehe
     *     {@code RecurringExpenseNotFoundException}).
     */
    Optional<RecurringExpense> findByIdAndUserId(Long id, Long userId);

    /**
     * Löscht alle Einträge eines Users (Kontolöschung, US-02, nDSG).
     *
     * <p>Bewusst {@code @Modifying} — Begründung wie bei
     * {@code TransactionRepository#deleteAllByUserId}: das DELETE muss physisch ausgeführt sein,
     * bevor {@code UserService.deleteUser} den User selbst löscht.
     */
    @Modifying
    @Query("delete from RecurringExpense r where r.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
