package com.budgetbuddy.notification;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository-Zugriff auf {@link Notification} (notification-internes Interface, kein
 * modulübergreifender Zugriff).
 *
 * <p><strong>Jede Methode ist an den {@code userId} gebunden.</strong> Ein geerbtes
 * {@code findById(id)} wäre auf dieser Entity ein IDOR — gleiche Begründung wie bei
 * {@code FixedCostRepository}. Aufrufer verwenden ausschliesslich die Methoden dieses Interfaces.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Benachrichtigungen eines Users, ungelesene zuerst und innerhalb dessen neueste zuerst.
     *
     * <p>Explizites {@code CASE WHEN} statt einer abgeleiteten Query-Methode: Letztere müsste sich
     * auf das DB-spezifische NULL-Ordering von {@code read_at ASC/DESC} verlassen, das je nach
     * Datenbank unterschiedlich ist (Postgres sortiert NULLs bei ASC standardmässig ans Ende).
     *
     * @param userId ID des authentifizierten Users.
     */
    @Query("select n from Notification n where n.userId = :userId "
            + "order by case when n.readAt is null then 0 else 1 end, n.createdAt desc")
    List<Notification> findByUserIdOrderByUnreadFirstThenNewest(@Param("userId") Long userId);

    /**
     * Einzelne Benachrichtigung eines Users — für {@code markAsRead}.
     *
     * @return leer, wenn die ID nicht existiert <em>oder</em> einem anderen User gehört. Beide
     *     Fälle bleiben für den Aufrufer bewusst ununterscheidbar (siehe
     *     {@code NotificationNotFoundException}).
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * Löscht alle Benachrichtigungen eines Users (Kontolöschung, US-02, nDSG).
     *
     * <p>Bewusst {@code @Modifying} — Begründung wie bei
     * {@code FixedCostRepository#deleteAllByUserId}: das DELETE muss physisch ausgeführt sein,
     * bevor {@code UserService.deleteUser} den User selbst löscht.
     */
    @Modifying
    @Query("delete from Notification n where n.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
