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
     * <p><strong>{@code locate(...)} statt {@code LIKE} — nicht zurückbauen (BE-CAT-14).</strong>
     * Die frühere Fassung baute das Suchmuster als
     * {@code upper(:text) LIKE concat('%', upper(c.empfaengerPattern), '%')}. Damit wurde ein
     * Pattern, das selbst ein {@code %} oder {@code _} trägt, zum Wildcard: {@code RABATT 20% MIGROS}
     * matchte auch {@code "RABATT 20 CHF MIGROS"}, {@code SHOP_X} auch {@code "SHOPPX"} — eine
     * falsche Kategorie über Stufe 1, deterministisch und ohne dass Claude den Text je sieht. Seit
     * BE-CAT-11 wird jeder von Claude kategorisierte Text gelernt, Prozentzeichen im Buchungstext
     * also regelmässig. {@code locate} kennt keine Metazeichen und braucht deshalb weder eine
     * {@code ESCAPE}-Klausel noch ein Escapen des Escape-Zeichens; die gespeicherten Patterns
     * bleiben unverändert, eine Migration war nicht nötig.
     *
     * @param text Transaktions-Freitext.
     * @return passende Einträge, spezifischster zuerst; leer, wenn kein Pattern matcht.
     */
    @Query(
            """
            SELECT c FROM CategoryLookup c
            WHERE locate(upper(c.empfaengerPattern), upper(:text)) > 0
            ORDER BY length(c.empfaengerPattern) DESC, c.empfaengerPattern ASC
            """)
    List<CategoryLookup> findMatching(@Param("text") String text);
}
