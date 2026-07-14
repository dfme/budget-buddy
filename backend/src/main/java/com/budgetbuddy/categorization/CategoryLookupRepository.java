package com.budgetbuddy.categorization;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository-Zugriff auf {@link CategoryLookup} (categorization-internes Interface, kein
 * modulübergreifender Zugriff).
 */
public interface CategoryLookupRepository extends JpaRepository<CategoryLookup, String> {

    /**
     * Liefert alle Lookup-Einträge, deren Pattern (case-insensitiv) im Transaktionstext enthalten
     * ist — z. B. matcht das Seed-Pattern {@code MIGROS} den Text {@code "MIGROS BERN 044..."}.
     *
     * <p>Sortiert nach Pattern-Länge absteigend: Bei mehreren Treffern gewinnt so das längste und
     * damit spezifischste Pattern deterministisch. Das Matching ist via {@code upper(...)} auf
     * beiden Seiten explizit case-insensitiv, unabhängig von der Spalten-Collation.
     *
     * @param text Transaktions-Freitext.
     * @return passende Einträge, spezifischster zuerst; leer, wenn kein Pattern matcht.
     */
    @Query(
            """
            SELECT c FROM CategoryLookup c
            WHERE upper(:text) LIKE concat('%', upper(c.empfaengerPattern), '%')
            ORDER BY length(c.empfaengerPattern) DESC, c.empfaengerPattern ASC
            """)
    List<CategoryLookup> findMatching(@Param("text") String text);
}
