package com.budgetbuddy.budget;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-Zugriff auf {@link FixedCost} (budget-internes Interface, kein modulübergreifender
 * Zugriff).
 *
 * <p><strong>Jede Methode ist an den {@code userId} gebunden.</strong> Ein geerbtes
 * {@code findById(id)} oder {@code deleteById(id)} wäre auf dieser Entity ein IDOR: wer eine
 * fremde ID kennt oder hochzählt, läse oder löschte fremde Fixkosten. Die Einschränkung steht
 * deshalb dort, wo die Query steht — nicht in einem Service, der auch von anderswo aufrufbar ist.
 * Aufrufer verwenden die Methoden dieses Interfaces und nicht die geerbten ID-Varianten.
 *
 * <p><strong>Keine Summierung in SQL.</strong> Die Fixkosten-Summe aus US-03 wird in Java über
 * {@link java.math.BigDecimal} gebildet, nicht per {@code @Query} (gleiches Vorgehen wie in
 * {@code TransactionRepository}). Unter SQLite war das zwingend, weil {@code DECIMAL(10,2)} dort
 * nur eine Affinität war und {@code SUM} Fliesskomma lieferte (#141). Seit DB-05 (ADR-12) könnte
 * PostgreSQL exakt summieren; die Aggregation bleibt trotzdem in Java, weil die Umrechnung auf
 * Monatsbeträge (÷ 1, ÷ 3, ÷ 12) ohnehin dort stattfindet und ADR-9 damit an einer Stelle
 * durchgesetzt wird statt an zweien.
 */
public interface FixedCostRepository extends JpaRepository<FixedCost, Long> {

    /**
     * Alle Fixkosten-Positionen eines Users, stabil nach Anlage-Reihenfolge sortiert.
     *
     * @param userId ID des authentifizierten Users.
     */
    List<FixedCost> findByUserIdOrderByIdAsc(Long userId);

    /**
     * Einzelne Position eines Users — für Detailzugriff und Update (BE-FC-02).
     *
     * @return leer, wenn die Position nicht existiert <em>oder</em> einem anderen User gehört.
     *     Beide Fälle sind für den Aufrufer bewusst ununterscheidbar, damit fremde IDs nicht über
     *     unterschiedliche Statuscodes erratbar werden.
     */
    Optional<FixedCost> findByIdAndUserId(Long id, Long userId);

    /**
     * Löscht eine Position, sofern sie dem User gehört.
     *
     * @return Anzahl gelöschter Zeilen: {@code 1} bei Erfolg, {@code 0} wenn die Position nicht
     *     existiert oder einem anderen User gehört. Der Aufrufer bildet {@code 0} auf 404 ab.
     */
    @Transactional
    long deleteByIdAndUserId(Long id, Long userId);

    /**
     * {@code true}, wenn der User mindestens eine Fixkosten-Position erfasst hat — Bedingung des
     * Onboarding-Wizards aus US-03, der erst nach dem ersten Eintrag übersprungen werden darf.
     */
    boolean existsByUserId(Long userId);
}
