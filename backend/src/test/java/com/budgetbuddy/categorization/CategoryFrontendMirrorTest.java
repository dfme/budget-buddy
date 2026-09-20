package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Hält den Frontend-Spiegel der Kategorienliste gegen {@link Category}.
 *
 * <p>{@code shared/category.ts} wiederholt Reihenfolge, Slug und Label des Enums. Bis BE-CAT-10
 * war das eine Zusage, die nur im Javadoc stand: Die Frontend-Tests prüfen {@code CATEGORIES}
 * gegen sich selbst, und kein Test hat je die beiden Enden verglichen. Genau da entsteht der
 * Schaden aber — läuft die Liste auseinander, trifft ein Slug seine {@code --cat-*}-Farbe nicht
 * mehr, oder ein Dropdown schickt ein Label, das {@link Category#fromLabel} nicht kennt.
 *
 * <p>Der Test liest die TypeScript-Datei als Text statt sie auszuführen. Das ist grob, reicht
 * aber für die Frage, die er stellt — und es ist der einzige Weg, der ohne Node-Prozess in der
 * Backend-Suite auskommt. Bricht er, weil jemand die Schreibweise der Liste umgestellt hat, ist
 * die Antwort, das Muster unten nachzuziehen; die Zusage selbst bleibt.
 */
class CategoryFrontendMirrorTest {

    /** Vom Maven-Arbeitsverzeichnis {@code backend/} aus. */
    private static final Path CATEGORY_TS =
            Path.of("..", "frontend", "src", "app", "shared", "category.ts");

    /** Ein Eintrag der {@code CATEGORIES}-Liste: {@code { slug: '…', label: '…', icon: '…' }}. */
    private static final Pattern ENTRY = Pattern.compile(
            "\\{\\s*slug:\\s*'([^']*)',\\s*label:\\s*'([^']*)',\\s*icon:\\s*'([^']*)'\\s*}");

    private record FrontendCategory(String slug, String label, String icon) {}

    /**
     * Nur der Bereich zwischen {@code export const CATEGORIES} und {@code ] as const;} — sonst
     * würde ein Beispiel im Javadoc darüber als echter Eintrag mitgelesen.
     */
    private static List<FrontendCategory> readFrontendCategories() {
        String source;
        try {
            source = Files.readString(CATEGORY_TS, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Frontend-Spiegel nicht lesbar unter " + CATEGORY_TS.toAbsolutePath(), e);
        }

        int start = source.indexOf("export const CATEGORIES");
        assertThat(start).as("CATEGORIES-Liste in category.ts gefunden").isNotNegative();
        int end = source.indexOf("] as const;", start);
        assertThat(end).as("Ende der CATEGORIES-Liste in category.ts gefunden").isNotNegative();

        List<FrontendCategory> categories = new ArrayList<>();
        Matcher matcher = ENTRY.matcher(source.substring(start, end));
        while (matcher.find()) {
            categories.add(new FrontendCategory(matcher.group(1), matcher.group(2), matcher.group(3)));
        }
        return categories;
    }

    /**
     * Die eigentliche Zusage: gleiche Reihenfolge, Slug ist der kleingeschriebene Enum-Name, und
     * das Label stimmt auf das Zeichen mit {@link Category#getLabel()} überein.
     *
     * <p>Alles drei in einer Assertion, damit der Fehlertext beide Listen nebeneinander zeigt —
     * drei getrennte Prüfungen würden bei einer verschobenen Reihenfolge dreimal dasselbe melden.
     */
    @Test
    void frontendListMirrorsTheEnumInOrderWithSlugAndLabel() {
        List<String> expected = Arrays.stream(Category.values())
                .map(category -> category.name().toLowerCase(Locale.ROOT) + "=" + category.getLabel())
                .toList();
        List<String> actual = readFrontendCategories().stream()
                .map(category -> category.slug() + "=" + category.label())
                .toList();

        assertThat(actual)
                .as("shared/category.ts spiegelt Category.java (Reihenfolge, Slug, Label)")
                .containsExactlyElementsOf(expected);
    }

    /**
     * Das Icon lebt nur im Frontend — geprüft wird deshalb nicht sein Wert, sondern dass jede
     * Kategorie eines hat. Ohne diese Zusage fiele eine neue Kategorie im Badge stillschweigend
     * auf den neutralen Punkt zurück, und das sähe aus wie ein unbekanntes Label.
     */
    @Test
    void everyFrontendCategoryCarriesANonEmptyIcon() {
        assertThat(readFrontendCategories())
                .allSatisfy(category -> assertThat(category.icon())
                        .as("Icon für %s", category.slug())
                        .isNotBlank());
    }
}
