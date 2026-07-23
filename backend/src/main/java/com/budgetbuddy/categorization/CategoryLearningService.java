package com.budgetbuddy.categorization;

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
 * Die Spalte ist mit {@code COLLATE NOCASE} angelegt (Flyway V04), sodass Patterns, die sich nur
 * in der Gross-/Kleinschreibung unterscheiden, denselben Eintrag treffen.
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

        String pattern = merchantPattern.trim();
        categoryLookupRepository.save(new CategoryLookup(pattern, category.getLabel()));
        log.debug("Lookup gelernt: '{}' → '{}'.", pattern, category.getLabel());
    }
}
