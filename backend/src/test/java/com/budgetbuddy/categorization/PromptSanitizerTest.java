package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests für {@link PromptSanitizer} (BE-CAT-06).
 *
 * <p>Jede Regel wird von <strong>zwei</strong> Seiten geprüft: was sie treffen muss und was sie
 * in Ruhe zu lassen hat. Die zweite Hälfte ist die wichtigere — eine Maskierungsregel, die zu
 * breit greift, kostet Kategorisierungs-Trefferquote und fällt in einem Test, der nur Treffer
 * prüft, nie auf.
 *
 * <p>{@link RealerKorpus} schliesst das ab: dieselben Texte, die der Parser aus den sechs
 * PDF-Fixtures erzeugt, hier festgehalten als Regressionsschutz für die Trefferquoten-AC.
 */
class PromptSanitizerTest {

    @Nested
    class Iban {

        @Test
        void kompaktGedruckteIbanWirdMaskiert() {
            assertThat(PromptSanitizer.sanitize("GIRO POST CH7709000000850055555 MIETE"))
                    .isEqualTo("GIRO POST <IBAN> MIETE");
        }

        @Test
        void inVierergruppenGedruckteIbanWirdMaskiert() {
            assertThat(PromptSanitizer.sanitize("UEBERWEISUNG CH77 0900 0000 8500 5555 5"))
                    .isEqualTo("UEBERWEISUNG <IBAN>");
        }

        /**
         * Der Fall, an dem eine zu gierige IBAN-Regel auffallen würde: zwei Grossbuchstaben am
         * Wortanfang hat fast jeder Händlername.
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025",
            "LASTSCHRIFT CSS VERSICHERUNG AG",
            "ESR STADTWERKE BERN"
        })
        void haendlernamenBleibenUnberuehrt(String text) {
            assertThat(PromptSanitizer.sanitize(text)).isEqualTo(text);
        }
    }

    @Nested
    class Kartennummer {

        @Test
        void postfinanceMaskierteNummerWirdErsetzt() {
            assertThat(PromptSanitizer.sanitize("KARTEN NR. XXXX4417"))
                    .isEqualTo("KARTEN NR. <KARTE>");
        }

        @Test
        void visecaVierergruppenWerdenErsetzt() {
            assertThat(PromptSanitizer.sanitize("KARTE 5500 20XX XXXX 5446"))
                    .isEqualTo("KARTE <KARTE>");
        }

        @Test
        void unmaskierterZiffernlaufInKartenlaengeWirdErsetzt() {
            assertThat(PromptSanitizer.sanitize("KONTO 5500201234565446"))
                    .isEqualTo("KONTO <KARTE>");
        }

        /**
         * Die Gegenprobe zur Vierergruppen-Regel: Ohne die {@code XX}-Bedingung fiele eine
         * beliebige Folge vierstelliger Zahlen mit heraus.
         */
        @Test
        void vierergruppenOhneMaskierungBleibenStehen() {
            assertThat(PromptSanitizer.sanitize("RECHNUNG 2024 2025")).isEqualTo("RECHNUNG 2024 2025");
        }

        /** Zu kurz für die Ziffernlauf-Regel — und der Händlertoken, den AC 3 schützt. */
        @Test
        void kurzeZiffernAmHaendlernamenBleibenStehen() {
            assertThat(PromptSanitizer.sanitize("TWINT COOP-1234 BERN BERN (CH)"))
                    .isEqualTo("TWINT COOP-1234 BERN BERN (CH)");
        }

        /**
         * Die Telefonnummer im Buchungstext: durch Leerzeichen getrennt, also für die
         * Ziffernlauf-Regel unsichtbar. Bewusster Nicht-Umfang dieses Tasks (BE-CAT-08) — der
         * Test hält den Ist-Zustand fest, damit die Entscheidung sichtbar bleibt.
         */
        @Test
        void telefonnummerBleibtStehen_bekannteGrenze() {
            assertThat(PromptSanitizer.sanitize("DIGITEC GALAXUS AG 044 913 2323"))
                    .isEqualTo("DIGITEC GALAXUS AG 044 913 2323");
        }
    }

    @Nested
    class Betrag {

        @ParameterizedTest
        @ValueSource(strings = {"42.50", "1'234.56", "10'800.00", "0.05"})
        void betraegeWerdenMaskiert(String betrag) {
            assertThat(PromptSanitizer.sanitize("ZAHLUNG " + betrag))
                    .isEqualTo("ZAHLUNG <BETRAG>");
        }

        /**
         * Der Fall, an dem die Betragsregel ohne Lookbehind falsch läge: in {@code 03.07.26}
         * sähe sie am Ende ein {@code 07.26}.
         */
        @ParameterizedTest
        @ValueSource(strings = {"03.07.2026", "03.07.26", "31.12.99"})
        void datumsangabenBleibenUnberuehrt(String datum) {
            assertThat(PromptSanitizer.sanitize("KAUF VOM " + datum))
                    .isEqualTo("KAUF VOM " + datum);
        }
    }

    @Nested
    class Referenz {

        @ParameterizedTest
        @ValueSource(strings = {"250704111222333444AB", "C040725R010A", "P123456789"})
        void undurchsichtigeReferenzenWerdenMaskiert(String referenz) {
            assertThat(PromptSanitizer.sanitize("BESTELLUNG " + referenz))
                    .isEqualTo("BESTELLUNG <REF>");
        }

        /**
         * Die Ziffernbedingung ist das, was Händlernamen heraushält — alle drei sind lang genug
         * für die Längenregel und würden ohne sie verschwinden.
         */
        @ParameterizedTest
        @ValueSource(strings = {"CONSULTING", "IMMOBILIEN", "RUECKZAHLUNG", "ZUSATZVERSICHERUNG"})
        void langeWoerterOhneZifferBleibenStehen(String wort) {
            assertThat(PromptSanitizer.sanitize("GUTSCHRIFT " + wort))
                    .isEqualTo("GUTSCHRIFT " + wort);
        }

        /**
         * Die Case-Sensitivität der Regel: unter {@code (?i)} verschwände diese Zweckzeile still
         * aus dem Prompt.
         */
        @Test
        void gemischtGeschriebeneZweckzeileBleibtStehen() {
            assertThat(PromptSanitizer.sanitize("Rechnung2026 Beitrag"))
                    .isEqualTo("Rechnung2026 Beitrag");
        }
    }

    @Nested
    class Personenname {

        @ParameterizedTest
        @ValueSource(strings = {"MUSTER, LEA", "MUSTER, ANNA", "MUSTER-MEIER, LEA"})
        void gegenparteiAlsPersonWirdMaskiert(String name) {
            assertThat(PromptSanitizer.sanitize("LASTSCHRIFT " + name + " BEITRAG"))
                    .isEqualTo("LASTSCHRIFT <NAME> BEITRAG");
        }

        /**
         * Der teuerste denkbare Fehler dieser Regel. Alle vier Zeilen stammen aus der
         * Viseca-Fixture und tragen den Händlernamen vor dem Komma — eine Regel nach dem Muster
         * «Wort, Wort» würde ihn hier zerstören und die Trefferquote mitnehmen (AC 3).
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "Coop-1122, Bern CH Lebensmittel",
            "Zalando SE, Berlin DE Bekleidung",
            "SBB CFF FFS, Bern CH Öffentlicher Verkehr",
            "RYANAIR ABC123, Dublin IE Fluggesellschaften"
        })
        void visecaHaendlerzeilenBleibenUnberuehrt(String zeile) {
            assertThat(PromptSanitizer.sanitize(zeile)).isEqualTo(zeile);
        }

        /**
         * Die dokumentierte Grenze: der Vorname in der frei getippten Zweckzeile überlebt. Der
         * Test hält sie fest, statt sie zu verschweigen — offen als BE-CAT-08.
         */
        @Test
        void vornameInDerZweckzeileBleibtStehen_bekannteGrenze() {
            assertThat(PromptSanitizer.sanitize("LASTSCHRIFT MUSTER, LEA SACKGELD LEA"))
                    .isEqualTo("LASTSCHRIFT <NAME> SACKGELD LEA");
        }
    }

    @Nested
    class Randfaelle {

        @Test
        void nullUndLeerBleibenUnveraendert() {
            assertThat(PromptSanitizer.sanitize(null)).isNull();
            assertThat(PromptSanitizer.sanitize("")).isEmpty();
            assertThat(PromptSanitizer.sanitize("   ")).isEqualTo("   ");
        }

        @Test
        void emailWirdMaskiert() {
            assertThat(PromptSanitizer.sanitize("ZAHLUNG lea@example.com"))
                    .isEqualTo("ZAHLUNG <EMAIL>");
        }

        /**
         * Ein Text, der vollständig aus einem maskierten Element besteht, wird nicht leer — er
         * wird zum Platzhalter dieses Elements. Das ist die Eigenschaft, die im Prompt eine leere
         * nummerierte Zeile ausschliesst: die Nummer trägt immer noch etwas, das Modell
         * beantwortet sie, und {@code applyResponse} muss sie nicht über den Fallback nachziehen.
         */
        @Test
        void vollstaendigMaskierterTextBleibtEinPlatzhalter() {
            assertThat(PromptSanitizer.sanitize("CH7709000000850055555")).isEqualTo("<IBAN>");
        }

        /** Mehrere Regeln auf einem Text, und keine hinterlässt doppelte Leerzeichen. */
        @Test
        void mehrereRegelnGreifenGemeinsam() {
            assertThat(PromptSanitizer.sanitize(
                            "GIRO POST CH7709000000850055555 MUSTER, LEA 1'234.56 REF 250704111222333444AB"))
                    .isEqualTo("GIRO POST <IBAN> <NAME> <BETRAG> REF <REF>");
        }
    }

    /**
     * Die Trefferquoten-AC an realen Daten: Der Händler- oder Zwecktoken muss die Maskierung
     * überleben.
     *
     * <p>Der Korpus ist der Ist-Zustand von {@code ParsedTransaction.fullText()} über alle sechs
     * PDF-Fixtures aus {@code src/test/resources/pdf/} — ausgelesen beim Planen dieses Tasks und
     * hier festgehalten. Bewusst als Konstanten statt über den Parser erzeugt: dieser Test gehört
     * ins {@code categorization}-Paket, und ein Zugriff auf {@code SwissBankStatementParser}
     * überschritte die Modulgrenze, die CLAUDE.md zieht.
     */
    @Nested
    class RealerKorpus {

        /**
         * Texte, die der Sanitizer <strong>unverändert</strong> durchlassen muss. Jeder trägt den
         * Token, an dem Lookup oder Claude die Kategorie festmachen.
         */
        private static final List<String> UNVERAENDERT = List.of(
                "KAUF/DIENSTLEISTUNG MIGROS M BERN WANKDORF BERN (CH)",
                "TWINT KAUF/DIENSTLEISTUNG VOM COOP-1234 BERN BERN (CH)",
                "LASTSCHRIFT SWISSCOM (SCHWEIZ) AG RECHNUNG 11-2025",
                "KAUF/DIENSTLEISTUNG DIGITEC GALAXUS AG ZUERICH (CH)",
                "KAUF/ONLINE-SHOPPING VOM ZALANDO SE",
                "KAUF/DIENSTLEISTUNG SBB CFF FFS BERN BERN (CH)",
                "LASTSCHRIFT CSS VERSICHERUNG AG PRAEMIE DEZEMBER 2025",
                "ESR STADTWERKE BERN",
                "GUTSCHRIFT MUSTER CONSULTING GMBH LOHN JULI 2026 SOWIE SPE SENVERGUETUNG",
                "GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025",
                "GIRO INTERNATIONAL Amazon EU S.a.r.l. Luxembourg",
                "Kartenzahlung Migros Zuerich",
                "LSV CSS Kranken-Versicherung",
                "Migros M Bern, Bern CH Lebensmittel");

        @ParameterizedTest
        @org.junit.jupiter.params.provider.FieldSource("UNVERAENDERT")
        void realeBuchungstexteUeberlebenUnveraendert(String text) {
            assertThat(PromptSanitizer.sanitize(text)).isEqualTo(text);
        }

        /**
         * Die Gegenrichtung am selben Korpus: die beiden Zeilen, die heute tatsächlich ein
         * Personendatum tragen, verlieren es — und behalten trotzdem ihren Zwecktoken.
         */
        @Test
        void personenbezogeneZeilenVerlierenDenNamen() {
            assertThat(PromptSanitizer.sanitize("GUTSCHRIFT MUSTER, ANNA RUECKZAHLUNG FERIENKASSE"))
                    .isEqualTo("GUTSCHRIFT <NAME> RUECKZAHLUNG FERIENKASSE");
            assertThat(PromptSanitizer.sanitize("LASTSCHRIFT MUSTER, LEA SACKGELD LEA"))
                    .doesNotContain("MUSTER")
                    .contains("SACKGELD");
        }

        /** Das Referenz-Token am Viseca-Händlernamen fällt weg, der Händler bleibt. */
        @Test
        void referenzAmHaendlernamenFaelltWeg() {
            assertThat(PromptSanitizer.sanitize(
                            "Spotify P123456789, Stockholm SE Digitalprodukte, Filme, Musik"))
                    .isEqualTo("Spotify <REF>, Stockholm SE Digitalprodukte, Filme, Musik");
        }
    }
}
