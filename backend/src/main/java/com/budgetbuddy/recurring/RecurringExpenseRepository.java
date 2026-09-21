package com.budgetbuddy.recurring;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository-Zugriff auf {@link RecurringExpense} (recurring-intern, kein modulübergreifender
 * Zugriff — andere Module gehen über {@link RecurringExpenseDetectionPort},
 * {@link RecurringExpenseAmountPort} und {@link RecurringExpenseCleanupPort}).
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
     * Alle Einträge eines Users, beide Status, sortiert — für {@code list} (BE-REC-02). Seit
     * FE-NOTIF-03 gehören auch {@code DISMISSED}-Einträge in die Antwort: die Abo-Übersicht zeigt
     * sie in einem eigenen Abschnitt «Kein Abo», damit der Klick auf eine Benachrichtigung zu
     * einem inzwischen verneinten Eintrag ein Ziel hat. Die Trennung nach Status macht der
     * Client anhand des {@code status}-Felds.
     *
     * <p>Alphabetisch nach {@code payee_key}, damit die Übersicht zwischen zwei Aufrufen nicht
     * springt — ohne {@code ORDER BY} hinge die Reihenfolge an der Datenbank. Der Schlüssel ist pro
     * User eindeutig (V11), die Sortierung damit total.
     */
    List<RecurringExpense> findByUserIdOrderByPayeeKeyAsc(Long userId);

    /**
     * Alle Einträge eines Users in einem Status — für {@link RecurringExpenseAmountPort}
     * (FE-FC-05): der Safe-to-Spend braucht genau die {@code DETECTED}-Zeilen, die verneinten
     * dürfen ihn nicht mindern. Der Index kommt wie oben aus dem {@code user_id}-Präfix der
     * {@code UNIQUE}-Constraint (V11); die Menge pro User ist klein.
     */
    List<RecurringExpense> findByUserIdAndStatus(Long userId, RecurringExpenseStatus status);

    /**
     * Einzelner Eintrag eines Users — für {@code dismiss} (BE-REC-02).
     *
     * @return leer, wenn die ID nicht existiert <em>oder</em> einem anderen User gehört. Beide
     *     Fälle bleiben für den Aufrufer bewusst ununterscheidbar (siehe
     *     {@code RecurringExpenseNotFoundException}).
     */
    Optional<RecurringExpense> findByIdAndUserId(Long id, Long userId);

    /**
     * Alle Einträge eines Users, die an derselben Bündel-Benachrichtigung hängen — für
     * {@code dismiss} (FE-NOTIF-04): erst wenn keiner davon mehr {@code DETECTED} ist, wird das
     * Bündel als gelesen markiert. Eine Liste statt eines {@code exists}-Counts, damit der Aufrufer
     * die soeben in derselben Persistence-Context geänderte Zeile mitzählt, ohne auf das
     * Flush-Verhalten der Query angewiesen zu sein.
     *
     * <p>Über {@code userId} gebunden, obwohl die {@code notificationId} global eindeutig ist —
     * dieselbe Zusage wie bei allen Methoden hier; der Index kommt aus dem {@code user_id}-Präfix
     * der {@code UNIQUE}-Constraint (V11, V14).
     */
    List<RecurringExpense> findByUserIdAndNotificationId(Long userId, Long notificationId);

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
