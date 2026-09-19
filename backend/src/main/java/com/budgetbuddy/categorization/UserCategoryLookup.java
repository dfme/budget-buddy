package com.budgetbuddy.categorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA-Entity der {@code user_category_lookup}-Tabelle (Flyway V12, BE-CAT-12): ein gelerntes
 * Händler-Pattern <em>eines</em> Users.
 *
 * <p>Gegenstück zur globalen {@link CategoryLookup} (V04, Seeds): Was der Lerneffekt schreibt —
 * manuelle Korrekturen (BE-CAT-04) wie Claude-Treffer (BE-CAT-11) — gehört seit ADR-15 dem User,
 * bei dem es entstanden ist, wirkt nur auf seine Kategorisierung und wird mit seinem Konto
 * gelöscht. Patterns stehen ausschliesslich in Grossschreibung ({@link CategoryLearningService}
 * normalisiert); das case-insensitive Matching übernimmt {@link UserCategoryLookupRepository}
 * via {@code upper(...)}.
 */
@Entity
@Table(name = "user_category_lookup")
public class UserCategoryLookup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "empfaenger_pattern", nullable = false)
    private String empfaengerPattern;

    @Column(name = "category", nullable = false)
    private String category;

    protected UserCategoryLookup() {
        // JPA
    }

    public UserCategoryLookup(Long userId, String empfaengerPattern, String category) {
        this.userId = userId;
        this.empfaengerPattern = empfaengerPattern;
        this.category = category;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getEmpfaengerPattern() {
        return empfaengerPattern;
    }

    /** Deutscher Kategorie-Label, wie in der DB gespeichert (z. B. {@code "Lebensmittel"}). */
    public String getCategory() {
        return category;
    }
}
