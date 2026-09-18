package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Die Kategorienliste selbst — bis BE-CAT-10 ungetestet, weil sie nur aus Konstanten bestand.
 *
 * <p>Mit der Erweiterung auf 17 Kategorien ist sie zur gespiegelten Datenstruktur geworden: Das
 * Frontend hält in {@code shared/category.ts} dieselbe Liste in derselben Reihenfolge, und der
 * Slug dort ist der kleingeschriebene Enum-Name. Was diese Klasse prüft, ist deshalb nicht das
 * Enum-Verhalten von Java, sondern die Zusagen, auf die sich der Spiegel verlässt.
 */
class CategoryTest {

    /**
     * Die Grösse wird hier <em>und</em> in {@code ClaudeCategorizationServiceTest} gepinnt, aber
     * aus verschiedenen Gründen: dort geht es um das Structured-Output-Schema, hier um den
     * Frontend-Spiegel. Wer eine Kategorie ergänzt, soll beide Enden sehen.
     */
    @Test
    void containsSeventeenCategories() {
        assertThat(Category.values()).hasSize(17);
    }

    /**
     * Die vier Kategorien aus BE-CAT-10 mit ihrem deutschen Label. Ausgeschrieben statt über
     * {@code values()} abgeleitet: Ein Tippfehler im Label verschiebt lautlos, was in
     * {@code transactions.category} landet — eine Ableitung würde ihn mitmachen.
     */
    @Test
    void mapsTheFourCategoriesAddedByBeCat10FromTheirLabel() {
        assertThat(Category.fromLabel("Persönliches")).isEqualTo(Category.PERSOENLICHES);
        assertThat(Category.fromLabel("Steuern")).isEqualTo(Category.STEUERN);
        assertThat(Category.fromLabel("Bargeldbezug")).isEqualTo(Category.BARGELDBEZUG);
        assertThat(Category.fromLabel("Reisen")).isEqualTo(Category.REISEN);
    }

    /** Jedes Label muss über {@link Category#fromLabel} zurückfinden — auch die bestehenden 13. */
    @ParameterizedTest
    @EnumSource(Category.class)
    void roundTripsEveryLabel(Category category) {
        assertThat(Category.fromLabel(category.getLabel())).isEqualTo(category);
    }

    /**
     * Zwei Kategorien mit demselben Label wären in der DB nicht mehr unterscheidbar, und
     * {@link Category#fromLabel} gäbe willkürlich die erste zurück.
     */
    @Test
    void hasNoDuplicateLabels() {
        assertThat(Arrays.stream(Category.values()).map(Category::getLabel))
                .doesNotHaveDuplicates();
    }

    /**
     * Der Frontend-Slug entsteht als {@code name().toLowerCase()} — wäre in einem Enum-Namen ein
     * Umlaut oder ein Unterstrich, liefe er gegen die {@code --cat-<slug>}-Tokens und die
     * {@code $categories}-Map auseinander. {@code PERSOENLICHES} schreibt das ö deshalb als oe.
     */
    @ParameterizedTest
    @EnumSource(Category.class)
    void hasAsciiLetterOnlyNameSoTheFrontendSlugStaysDerivable(Category category) {
        assertThat(category.name()).matches("[A-Z]+");
    }

    /** {@link Category#SONSTIGES} ist der Fallback und steht deshalb am Ende der Liste. */
    @Test
    void keepsSonstigesLast() {
        Category[] values = Category.values();
        assertThat(values[values.length - 1]).isEqualTo(Category.SONSTIGES);
    }

    @Test
    void rejectsAnUnknownLabel() {
        assertThatThrownBy(() -> Category.fromLabel("Weltraumtourismus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Weltraumtourismus");
    }

    /**
     * Ein leeres oder {@code null}-Label kommt aus einer Spalte, die {@code NOT NULL} ist — es
     * darf nicht still auf eine Kategorie fallen, sondern muss dieselbe laute Meldung erzeugen.
     */
    @Test
    void rejectsNullAndBlankLabels() {
        Stream.of(null, "", "  ")
                .forEach(label -> assertThatThrownBy(() -> Category.fromLabel(label))
                        .isInstanceOf(IllegalArgumentException.class));
    }
}
