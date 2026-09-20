package com.budgetbuddy.categorization;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository-Zugriff auf {@link UserCategoryLookup} (categorization-internes Interface, kein
 * modulübergreifender Zugriff). Jede Query ist auf einen User eingeschränkt — die Tabelle hält
 * rohe Buchungstexte, ein Zugriff ohne {@code userId} wäre ein Mandantenleck.
 */
public interface UserCategoryLookupRepository extends JpaRepository<UserCategoryLookup, Long> {

    /**
     * Liefert die gelernten Einträge <em>dieses</em> Users, deren Pattern (case-insensitiv) im
     * Transaktionstext enthalten ist — dieselbe Substring-Semantik wie
     * {@link CategoryLookupRepository#findMatching}, sortiert nach Pattern-Länge absteigend, damit
     * das spezifischste Pattern deterministisch vorn steht.
     *
     * <p><strong>{@code locate(...)} statt {@code LIKE} — nicht zurückbauen (BE-CAT-14).</strong>
     * Die Begründung steht bei {@link CategoryLookupRepository#findMatching} und wiegt hier
     * schwerer: Diese Tabelle hält seit BE-CAT-12 <em>ausschliesslich</em> Gelerntes, also rohen
     * Buchungstext, in dem ein {@code %} oder {@code _} regelmässig vorkommt — die kuratierten
     * Seeds drüben tragen keines. Als {@code LIKE}-Muster würde ein solches Pattern zum Wildcard
     * und eine falsche Kategorie über Stufe 1 liefern, ohne dass Claude den Text je sieht.
     *
     * @param userId User, dessen Lerneinträge befragt werden.
     * @param text Transaktions-Freitext.
     * @return passende Einträge, spezifischster zuerst; leer, wenn kein Pattern matcht.
     */
    @Query(
            """
            SELECT u FROM UserCategoryLookup u
            WHERE u.userId = :userId
              AND locate(upper(u.empfaengerPattern), upper(:text)) > 0
            ORDER BY length(u.empfaengerPattern) DESC, u.empfaengerPattern ASC
            """)
    List<UserCategoryLookup> findMatching(@Param("userId") Long userId, @Param("text") String text);

    /**
     * Fügt ein Pattern für den User ein oder aktualisiert dessen Kategorie — der jüngste Aufruf
     * gewinnt. Das ist die Upsert-Semantik, die {@code category_lookup} über seinen
     * Primärschlüssel hatte; hier trägt sie {@code UNIQUE (user_id, empfaenger_pattern)} (V12).
     *
     * <p>Nativ, weil JPQL kein {@code ON CONFLICT} kennt und ein «lesen, dann schreiben» im
     * Service unter zwei gleichzeitigen Imports desselben Users am Unique-Constraint scheitern
     * könnte. Postgres-Syntax ist seit ADR-12 die einzige, die die App bedienen muss.
     *
     * @param pattern bereits normalisiertes (grossgeschriebenes) Händler-Pattern.
     * @param category Kategorie-Label aus dem {@link Category}-Enum.
     */
    @Modifying
    @Query(
            value = """
            INSERT INTO user_category_lookup (user_id, empfaenger_pattern, category)
            VALUES (:userId, :pattern, :category)
            ON CONFLICT (user_id, empfaenger_pattern) DO UPDATE SET category = EXCLUDED.category
            """,
            nativeQuery = true)
    void upsert(
            @Param("userId") Long userId,
            @Param("pattern") String pattern,
            @Param("category") String category);

    /**
     * Löscht alle gelernten Patterns eines Users (Kontolöschung, US-02, nDSG).
     *
     * <p>Bewusst {@code @Modifying} — Begründung wie bei
     * {@code TransactionRepository#deleteAllByUserId}: das DELETE muss physisch ausgeführt sein,
     * bevor {@code UserService.deleteUser} den User selbst löscht.
     */
    @Modifying
    @Query("delete from UserCategoryLookup u where u.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
