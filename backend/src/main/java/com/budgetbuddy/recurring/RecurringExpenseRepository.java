package com.budgetbuddy.recurring;

import java.util.List;
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
