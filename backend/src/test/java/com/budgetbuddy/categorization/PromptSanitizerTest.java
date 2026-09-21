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
         * Vormals {@code telefonnummerBleibtStehen_bekannteGrenze}: derselbe Text, die
         * umgekehrte Erwartung. Bis BE-CAT-08 hielt dieser Test den Ist-Zustand fest — die
         * Nummer ist durch Leerzeichen getrennt und damit für die Ziffernlauf-Regel unsichtbar.
         * Jetzt nimmt sie {@code PHONE}, und der Händlertoken davor bleibt trotzdem stehen.
         */
        @Test
        void telefonnummerErreichtDenPromptNichtMehr() {
            assertThat(PromptSanitizer.sanitize("DIGITEC GALAXUS AG 044 913 2323"))
                    .isEqualTo("DIGITEC GALAXUS AG <TEL>");
        }
    }

    @Nested
    class Telefonnummer {

        /**
         * Die Gruppierung ist gleichgültig, die Länge nicht: national {@code 0} plus neun
         * Ziffern, international {@code +41}/{@code 0041} plus neun.
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "044 913 2323",
            "044 913 23 23",
            "0800 123 456",
            "079 123 45 67",
            "+41 44 913 23 23",
            "0041 44 913 2323",
            "044/913 23 23"
        })
        void telefonnummernWerdenMaskiert(String nummer) {
            assertThat(PromptSanitizer.sanitize("DIGITEC GALAXUS AG " + nummer))
                    .isEqualTo("DIGITEC GALAXUS AG <TEL>");
        }

        /**
         * Die Gegenprobe, und die wichtigere Hälfte: eine Regel über Ziffernfolgen ist genau so
         * lange harmlos, wie sie Jahreszahlen, Datumsangaben und Filialnummern in Ruhe lässt.
         * {@code RECHNUNG 2024 2025} trägt zehn Ziffern in zwei Gruppen — nur eben ohne
         * führende {@code 0}.
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "RECHNUNG 2024 2025",
            "KAUF VOM 03.07.2026",
            "LASTSCHRIFT SWISSCOM (SCHWEIZ) AG RECHNUNG 11-2025",
            "TWINT COOP-1234 BERN BERN (CH)",
            "ESR STADTWERKE BERN"
        })
        void zahlenOhneTelefonformBleibenStehen(String text) {
            assertThat(PromptSanitizer.sanitize(text)).isEqualTo(text);
        }

        /**
         * Die kompakt gedruckte Nummer erfüllt beide Regeln. Dass sie {@code <REF>} wird und
         * nicht {@code <TEL>}, ist der Zweck der Reihenfolge: {@code PHONE} läuft nach
         * {@code OPAQUE_REFERENCE}, damit eine zehnstellige Kontonummer nicht zur Telefonnummer
         * umbenannt wird. Maskiert ist sie in beiden Fällen.
         */
        @Test
        void kompakteNummerBleibtEineReferenz() {
            assertThat(PromptSanitizer.sanitize("KONTAKT 0449132323")).isEqualTo("KONTAKT <REF>");
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
         * Vormals {@code vornameInDerZweckzeileBleibtStehen_bekannteGrenze}: bis BE-CAT-08 hielt
         * dieser Test fest, dass das nachgestellte {@code LEA} überlebt. Die Echo-Maskierung
         * nimmt es jetzt mit — und {@code SACKGELD}, der Zwecktoken, an dem die Kategorisierung
         * hängt, bleibt stehen.
         */
        @Test
        void vornameInDerZweckzeileWirdMitmaskiert() {
            assertThat(PromptSanitizer.sanitize("LASTSCHRIFT MUSTER, LEA SACKGELD LEA"))
                    .isEqualTo("LASTSCHRIFT <NAME> SACKGELD <NAME>");
        }

        /** Auch der Nachname verschwindet, wenn er im Text ein zweites Mal auftaucht. */
        @Test
        void nachnameWirdInSeinemZweitenVorkommenMitmaskiert() {
            assertThat(PromptSanitizer.sanitize("GUTSCHRIFT MUSTER, ANNA RUECKZAHLUNG MUSTER"))
                    .isEqualTo("GUTSCHRIFT <NAME> RUECKZAHLUNG <NAME>");
        }

        /** Beim Doppelnamen tragen beide Hälften das Echo, nicht nur die erste. */
        @Test
        void beideHaelftenDesDoppelnamensTragenDasEcho() {
            assertThat(PromptSanitizer.sanitize("LASTSCHRIFT MUSTER-MEIER, LEA MIETE MEIER"))
                    .isEqualTo("LASTSCHRIFT <NAME> MIETE <NAME>");
        }

        /**
         * Die entscheidende Gegenprobe: Die Echo-Maskierung ist selbst-bedingt. Ohne einen
         * {@code NACHNAME, VORNAME}-Treffer im selben Text kann sie gar nichts anfassen — genau
         * das ist der Unterschied zu einer Vornamensliste, und der Grund, warum die
         * Trefferquoten-AC strukturell unberührt bleibt und nicht bloss empirisch.
         */
        @ParameterizedTest
        @ValueSource(strings = {
            "LASTSCHRIFT SACKGELD LEA",
            "GUTSCHRIFT MUSTER CONSULTING GMBH LOHN JULI 2026",
            "KAUF/DIENSTLEISTUNG MIGROS M BERN WANKDORF BERN (CH)"
        })
        void echoMaskierungGreiftNurBeiPersonentreffer(String text) {
            assertThat(PromptSanitizer.sanitize(text)).isEqualTo(text);
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
                    .doesNotContain("LEA")
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
