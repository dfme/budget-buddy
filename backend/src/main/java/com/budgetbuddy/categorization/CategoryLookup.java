package com.budgetbuddy.categorization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA-Entity der {@code category_lookup}-Tabelle (Flyway V04, DB-04).
 *
 * <p>Bildet ein bekanntes Händler-Pattern ({@code empfaenger_pattern}, zugleich PK) auf einen
 * Kategorie-Label ({@code category}) ab. Die Spalte {@code empfaenger_pattern} ist in der Migration
 * mit {@code COLLATE NOCASE} angelegt; das case-insensitive Matching übernimmt aber die Query im
 * {@link CategoryLookupRepository} explizit via {@code upper(...)}, damit es dialekt-unabhängig ist.
 */
@Entity
@Table(name = "category_lookup")
public class CategoryLookup {

    @Id
    @Column(name = "empfaenger_pattern", nullable = false)
    private String empfaengerPattern;

    @Column(name = "category", nullable = false)
    private String category;

    protected CategoryLookup() {
        // JPA
    }

    public CategoryLookup(String empfaengerPattern, String category) {
        this.empfaengerPattern = empfaengerPattern;
        this.category = category;
    }

    public String getEmpfaengerPattern() {
        return empfaengerPattern;
    }

    /** Deutscher Kategorie-Label, wie in der DB gespeichert (z. B. {@code "Lebensmittel"}). */
    public String getCategory() {
        return category;
    }
}
