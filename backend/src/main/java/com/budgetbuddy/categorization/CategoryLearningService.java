package com.budgetbuddy.categorization;

import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementierung des {@link CategoryLearningPort} (ADR-6, Schritt 3): schreibt user-bestätigte
 * Zuordnungen in die {@code category_lookup}-Tabelle.
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
        String pattern = merchantPattern.trim().toUpperCase(Locale.ROOT);
        categoryLookupRepository.save(new CategoryLookup(pattern, category.getLabel()));
        // Händler-Pattern redigiert (BE-PDF-06): es stammt aus dem Transaktionstext — auch DEBUG
        // darf keine Zahlungsdaten tragen.
        log.debug("Lookup gelernt: {} → '{}'.", LogRedaction.redact(pattern), category.getLabel());
    }
}
