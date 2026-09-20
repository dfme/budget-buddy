package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

/**
 * Unit-Test der beiden Textformen einer geparsten Buchung.
 *
 * <p>{@code fullText()} ist der Input der Kategorisierung (ADR-6), {@code detailsAsText()} der
 * Wert für {@code transactions.buchungsdetails} (BE-PDF-07). Sie sehen ähnlich aus und sind es
 * nicht — die Tests halten den Unterschied fest, damit niemand das eine durch das andere ersetzt.
 */
class ParsedTransactionTest {

    private static ParsedTransaction with(List<String> details) {
        return new ParsedTransaction(LocalDate.of(2026, 7, 11), "LASTSCHRIFT", details,
                new BigDecimal("89.90"), false);
    }

    @Nested
    class DetailsAsText {

        @Test
        void joinsLinesWithNewline() {
            assertThat(with(List.of("MUSTER, LEA", "SACKGELD LEA")).detailsAsText())
                    .isEqualTo("MUSTER, LEA\nSACKGELD LEA");
        }

        @Test
        void singleLineStaysAsIs() {
            assertThat(with(List.of("ZALANDO SE")).detailsAsText()).isEqualTo("ZALANDO SE");
        }

        /**
         * {@code null} statt Leerstring: In der Datenbank hält das «hatte keine Detailzeilen» von
         * «vor BE-PDF-07 importiert» getrennt. Ein Leerstring könnte beides bedeuten.
         */
        @Test
        void emptyDetailsBecomeNull() {
            assertThat(with(List.of()).detailsAsText()).isNull();
        }

        /**
         * Der Zweck der {@code \n}-Verbindung: US-08 (Abo-Erkennung) braucht die einzelnen Zeilen
         * wieder. Detailzeilen enthalten konstruktionsbedingt keinen Umbruch, der Split ist damit
         * die exakte Umkehrung — anders als bei einer Verbindung über Leerzeichen, die einen
         * mehrwortigen Empfängernamen unwiederbringlich zerlegte.
         */
        @Test
        void isReversibleBySplitting() {
            List<String> details = List.of("MUSTER, LEA", "SACKGELD LEA");

            assertThat(with(details).detailsAsText().split("\n")).containsExactlyElementsOf(details);
        }

        /** Ohne den Buchungstext — der hat seine eigene Spalte und wäre hier ein Duplikat. */
        @Test
        void doesNotContainBuchungstext() {
            assertThat(with(List.of("ZALANDO SE")).detailsAsText()).doesNotContain("LASTSCHRIFT");
        }
    }

    @Nested
    class FullText {

        /** Unverändert: Beide Stufen der Kategorisierung brauchen weiterhin den vollen Kontext. */
        @Test
        void stillContainsBuchungstextAndDetails() {
            assertThat(with(List.of("MUSTER, LEA", "SACKGELD LEA")).fullText())
                    .isEqualTo("LASTSCHRIFT MUSTER, LEA SACKGELD LEA");
        }

        @Test
        void withoutDetailsIsBuchungstextAlone() {
            assertThat(with(List.of()).fullText()).isEqualTo("LASTSCHRIFT");
        }
    }

    /**
     * Die persistierte Buchung muss denselben Lookup-Schlüssel liefern wie die geparste
     * (BE-CAT-11): Die Claude-Stufe lernt beim Import über {@link ParsedTransaction#fullText()},
     * die manuelle Korrektur über {@link Transaction#fullText()}. Driften die beiden auseinander,
     * schreiben sie zwei Zeilen statt einer — und die Korrektur des Users verliert gegen den
     * längeren Claude-Eintrag, weil {@code findMatching} nach Pattern-Länge sortiert.
     *
     * <p>Der Weg dazwischen ist der Import: {@code detailsAsText()} wird persistiert
     * ({@code ImportJobRunner:210}) und von {@link Transaction#fullText()} wieder zum Schlüssel
     * zusammengesetzt. Diese Tests laufen ihn nach.
     */
    @Nested
    class SameKeyAsPersistedTransaction {

        private Transaction persisted(ParsedTransaction parsed) {
            return new Transaction(1L, parsed.buchungsdatum(), parsed.buchungstext(),
                    parsed.detailsAsText(), parsed.betrag(), parsed.isIncome(), "Sonstiges", "sha");
        }

        @Test
        void withDetails() {
            ParsedTransaction parsed = with(List.of("MUSTER, LEA", "SACKGELD LEA"));

            assertThat(persisted(parsed).fullText()).isEqualTo(parsed.fullText());
        }

        @Test
        void withoutDetails() {
            ParsedTransaction parsed = with(List.of());

            assertThat(persisted(parsed).fullText()).isEqualTo(parsed.fullText());
        }

        /** Vor BE-PDF-07 importiert: keine Detailzeilen in der Spalte, nur der Buchungstext. */
        @Test
        void legacyRowWithoutDetailsColumn() {
            Transaction legacy = new Transaction(1L, LocalDate.of(2026, 7, 11), "LASTSCHRIFT", null,
                    new BigDecimal("89.90"), false, "Sonstiges", "sha");

            assertThat(legacy.fullText()).isEqualTo("LASTSCHRIFT");
        }
    }
}
