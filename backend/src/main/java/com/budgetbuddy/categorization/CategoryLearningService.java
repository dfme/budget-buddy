package com.budgetbuddy.categorization;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung des {@link CategoryLearningPort} (ADR-6, Schritt 4): schreibt gelernte
 * Zuordnungen in die {@code category_lookup}-Tabelle — aus manuellen Korrekturen (BE-CAT-04) wie
 * aus erfolgreichen Claude-Kategorisierungen (BE-CAT-11). Dieser Service unterscheidet die beiden
 * Quellen nicht; welche Ergebnisse überhaupt lernwürdig sind, entscheiden die Aufrufer.
 *
 * <p>{@code empfaenger_pattern} ist der Primärschlüssel — {@code save} wirkt daher als Upsert:
 * Ein neues Pattern wird eingefügt, ein bereits vorhandenes in seiner Kategorie aktualisiert.
 *
 * <p>Damit Patterns, die sich nur in der Gross-/Kleinschreibung unterscheiden, denselben Eintrag
 * treffen, wird vor dem Speichern auf Grossschreibung normalisiert. Unter SQLite übernahm das die
 * Spalten-Collation {@code COLLATE NOCASE} (Flyway V04); PostgreSQL kennt sie nicht, und ein
 * case-sensitiver Primärschlüssel würde aus {@code migros} und {@code MIGROS} zwei konkurrierende
 * Zeilen machen (DB-05, ADR-12). Die Normalisierung ersetzt die Collation dialektunabhängig und
 * passt zu den durchgängig grossgeschriebenen Seeds aus V04.
 *
 * <p><strong>Gespeichert wird nicht der volle Text, sondern sein stabiles Präfix</strong>
 * (BE-CAT-13): {@link LookupPatternExtractor} schneidet die variable Mitteilung ab — aus
 * {@code GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025} wird {@code GIRO POST MUSTER
 * IMMOBILIEN AG MIETE}, und die Februar-Miete trifft die Zeile, ohne dass Claude sie sieht. Die
 * Extraktion sitzt hier und nicht beim Aufrufer, weil sie für <em>beide</em> Quellen gelten muss:
 * Schriebe die manuelle Korrektur weiterhin den vollen Text, entstünde eine zweite, längere Zeile,
 * die in {@link CategoryLookupRepository#findMatching} nur für diesen einen Monat gewänne — der
 * Blocker aus PR #320 in neuer Form.
 */
@Service
public class CategoryLearningService implements CategoryLearningPort {

    private static final Logger log = LoggerFactory.getLogger(CategoryLearningService.class);

    private final CategoryLookupRepository categoryLookupRepository;

    public CategoryLearningService(CategoryLookupRepository categoryLookupRepository) {
        this.categoryLookupRepository = categoryLookupRepository;
    }

    @Override
    @Transactional
    public void learn(String merchantPattern, Category category) {
        if (merchantPattern == null || merchantPattern.isBlank()) {
            // Ohne Pattern lässt sich nichts matchen — kein Lerneintrag, aber auch kein Fehler.
            log.debug("Kein Lerneintrag: leeres Händler-Pattern für Kategorie '{}'.",
                    category.getLabel());
            return;
        }

        // Locale.ROOT statt Default-Locale: unter tr-TR würde "i" sonst zu "İ" und ein gelerntes
        // Pattern liesse sich mit derselben Eingabe nicht wiederfinden.
        String normalized = merchantPattern.trim().toUpperCase(Locale.ROOT);
        // Erst normalisieren, dann schneiden: die Monatsliste im Extractor ist grossgeschrieben,
        // und das Präfix muss zeichengleich zum Anfang des Textes bleiben, den findMatching
        // später per upper(...) vergleicht.
        String pattern = LookupPatternExtractor.extract(normalized);
        categoryLookupRepository.save(new CategoryLookup(pattern, category.getLabel()));
        // Händler-Pattern redigiert (BE-PDF-06): es stammt aus dem Transaktionstext — auch DEBUG
        // darf keine Zahlungsdaten tragen.
        log.debug("Lookup gelernt: {} → '{}'.", LogRedaction.redact(pattern), category.getLabel());
    }
}
