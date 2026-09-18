package com.budgetbuddy.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit-Tests für {@link LookupPatternExtractor} (BE-CAT-13).
 *
 * <p>Die Beispiele sind der Korpus: jede Zeile unten ist ein {@code fullText()} aus einer der acht
 * PDF-Fixtures (Grossschreibung wie nach der Normalisierung in {@code CategoryLearningService})
 * oder ein bewusst konstruierter Randfall. Wer eine Regel im Extractor ändert, sieht hier, welche
 * Buchungsart sie trifft.
 */
class LookupPatternExtractorTest {

    // --- Korpus: variable Mitteilung am Ende → Präfix -------------------------------------------

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025                        | GIRO POST MUSTER IMMOBILIEN AG MIETE
            GIRO POST MUSTER IMMOBILIEN AG MIETE FEBRUAR 2025                       | GIRO POST MUSTER IMMOBILIEN AG MIETE
            LASTSCHRIFT CSS VERSICHERUNG AG PRAEMIE MAERZ 2025                      | LASTSCHRIFT CSS VERSICHERUNG AG PRAEMIE
            GUTSCHRIFT MUSTER CONSULTING GMBH LOHN DEZEMBER 2025                    | GUTSCHRIFT MUSTER CONSULTING GMBH LOHN
            GUTSCHRIFT STEUERVERWALTUNG KT. BERN RUECKERSTATTUNG APRIL 2025         | GUTSCHRIFT STEUERVERWALTUNG KT. BERN RUECKERSTATTUNG
            LASTSCHRIFT SWISSCOM (SCHWEIZ) AG RECHNUNG 12-2025                      | LASTSCHRIFT SWISSCOM (SCHWEIZ) AG RECHNUNG
            LASTSCHRIFT SWISSCOM (SCHWEIZ) AG RECHNUNG 08-2019                      | LASTSCHRIFT SWISSCOM (SCHWEIZ) AG RECHNUNG
            GUTSCHRIFT MUSTER CONSULTING GMBH SPESEN AUGUST 2019                    | GUTSCHRIFT MUSTER CONSULTING GMBH SPESEN
            KONTOÜBERTRAG AUF HELSANA PRAEMIE JULI 2026                             | KONTOÜBERTRAG AUF HELSANA PRAEMIE
            GUTSCHRIFT MUSTER CONSULTING GMBH LOHN JULI 2026 SOWIE SPE SENVERGUETUNG | GUTSCHRIFT MUSTER CONSULTING GMBH LOHN
            ZAHLUNG SECURITAS AG SCHWEIZERISCHE BEWACHUNGSGESELLSCHAFT BERN BEZAHLT FÜR: ANNA MARIA MUSTER REG. NR 10000001 | ZAHLUNG SECURITAS AG SCHWEIZERISCHE BEWACHUNGSGESELLSCHAFT BERN BEZAHLT FÜR: ANNA MARIA MUSTER REG. NR
            """)
    void cutsBeforeTheFirstVariableToken(String fullText, String expected) {
        assertThat(LookupPatternExtractor.extract(fullText)).isEqualTo(expected);
    }

    // --- Korpus: Kartenzahlungen ohne variablen Teil bleiben unverändert (AC 4) -----------------

    @ParameterizedTest
    @ValueSource(strings = {
        "KAUF/DIENSTLEISTUNG MIGROS M BERN WANKDORF BERN (CH)",
        "TWINT KAUF/DIENSTLEISTUNG VOM COOP-1234 BERN BERN (CH)",
        "GOOGLE PAY KAUF/DIENSTLEISTUNG VOM ALDI SUISSE BERN BERN (CH)",
        "KAUF/ONLINE-SHOPPING VOM DIGITEC GALAXUS AG",
        "KARTENZAHLUNG COOP-2001 BERN",
        "LSV CSS KRANKEN-VERSICHERUNG",
        "ONLINEKAUF DIGITEC GALAXUS AG",
        "DAUERAUFTRAG MIETE MUSTER IMMOBILIEN AG",
        "COOP-1122, BERN CH LEBENSMITTEL",
        "RYANAIR ABC123, DUBLIN IE FLUGGESELLSCHAFTEN",
        "DIGITEC GALAXUS AG 044 913 2323",
        "KAUF/DIENSTLEISTUNG BAECKEREI HUBER BERN KARTE 1234",
        "PREIS FÜR KONTOFÜHRUNG",
        "ESR STADTWERKE BERN",
        "LASTSCHRIFT MUSTER, LEA SACKGELD LEA",
        "ZAHLUNG HELSANA VERSICHERUNGEN AG BEZAHLT FÜR: MUSTER ANNA",
    })
    void leavesTextsWithoutVariableTokensUntouched(String fullText) {
        assertThat(LookupPatternExtractor.extract(fullText)).isEqualTo(fullText);
    }

    // --- Regeln im Detail -----------------------------------------------------------------------

    @Test
    void aMonthCountsOnlyWhenAYearFollows() {
        // MAI THAI ist ein Restaurant, MARS ein Händler — ohne Jahr bleibt der Monat stehen.
        assertThat(LookupPatternExtractor.extract("KAUF/DIENSTLEISTUNG MAI THAI BERN (CH)"))
                .isEqualTo("KAUF/DIENSTLEISTUNG MAI THAI BERN (CH)");
        assertThat(LookupPatternExtractor.extract("TWINT KAUF/DIENSTLEISTUNG VOM MARS"))
                .isEqualTo("TWINT KAUF/DIENSTLEISTUNG VOM MARS");
        // Ein Jahr dahinter — zwei- oder vierstellig, auch verbunden — macht ihn variabel.
        assertThat(LookupPatternExtractor.extract("GIRO POST MUSTER AG MIETE MAI 25"))
                .isEqualTo("GIRO POST MUSTER AG MIETE");
        assertThat(LookupPatternExtractor.extract("GIRO POST MUSTER AG MIETE JAN-2025"))
                .isEqualTo("GIRO POST MUSTER AG MIETE");
        assertThat(LookupPatternExtractor.extract("GIRO POST MUSTER AG MIETE JANUAR, 2025"))
                .isEqualTo("GIRO POST MUSTER AG MIETE");
    }

    @Test
    void aDayBeforeTheMonthMovesTheCutForward() {
        assertThat(LookupPatternExtractor.extract("GIRO POST MUSTER AG MIETE PER 15. MAI 2025"))
                .isEqualTo("GIRO POST MUSTER AG MIETE PER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"15.05.2025", "15.05.25", "15.05.", "15/05/2025", "08.2025", "2025-08",
        "2025-08-15", "2025", "P123456789", "CH9300762011623852957", "CH66"})
    void recognisesDatesReferencesAndIbans(String variable) {
        assertThat(LookupPatternExtractor.extract("GIRO POST MUSTER AG MIETE " + variable))
                .isEqualTo("GIRO POST MUSTER AG MIETE");
    }

    @Test
    void doesNotTreatShortNumbersAsVariable() {
        // 1/2, 3-4 und vierstellige Zahlen sind keine Daten und keine Referenzen.
        assertThat(LookupPatternExtractor.extract("KAUF/DIENSTLEISTUNG PIZZA 1/2 PREIS BERN"))
                .isEqualTo("KAUF/DIENSTLEISTUNG PIZZA 1/2 PREIS BERN");
        assertThat(LookupPatternExtractor.extract("KAUF/DIENSTLEISTUNG WOHNUNG 3-4 ZIMMER BERN"))
                .isEqualTo("KAUF/DIENSTLEISTUNG WOHNUNG 3-4 ZIMMER BERN");
    }

    // --- Guard ----------------------------------------------------------------------------------

    @Test
    void keepsTheFullTextWhenThePrefixWouldBeGeneric() {
        // Weniger als drei Tokens: LASTSCHRIFT allein zwänge jede Lastschrift in eine Kategorie.
        assertThat(LookupPatternExtractor.extract("LASTSCHRIFT 123456789"))
                .isEqualTo("LASTSCHRIFT 123456789");
        assertThat(LookupPatternExtractor.extract("GIRO POST 12345 MUSTER AG"))
                .isEqualTo("GIRO POST 12345 MUSTER AG");
        // Drei Tokens, aber weniger als die Hälfte: das TWINT-Layout, dessen Boilerplate allein
        // drei Tokens lang ist — genau der Fall, für den die Hälfte-Regel da ist.
        assertThat(LookupPatternExtractor.extract("TWINT KAUF/DIENSTLEISTUNG VOM 12345 SHOP BERN (CH)"))
                .isEqualTo("TWINT KAUF/DIENSTLEISTUNG VOM 12345 SHOP BERN (CH)");
        // Eine fünfstellige PLZ direkt hinter einem kurzen Händlernamen: ZALANDO SE wäre zwar
        // spezifisch, aber der Guard kennt den Unterschied zu GIRO POST nicht — voll ist sicher.
        assertThat(LookupPatternExtractor.extract("ZALANDO SE 10115 BERLIN"))
                .isEqualTo("ZALANDO SE 10115 BERLIN");
        // Viseca: die Referenz klebt am Händler, das Präfix wäre ein einziges Wort.
        assertThat(LookupPatternExtractor.extract("SPOTIFY P123456789, STOCKHOLM SE DIGITALPRODUKTE, FILME, MUSIK"))
                .isEqualTo("SPOTIFY P123456789, STOCKHOLM SE DIGITALPRODUKTE, FILME, MUSIK");
    }

    @Test
    void exactlyHalfIsEnough() {
        // 10 Tokens, Schnitt nach 5 — der Lohn mit umbrochener Mitteilung aus dem 2026er-Fixture.
        assertThat(LookupPatternExtractor.extract(
                        "GUTSCHRIFT MUSTER CONSULTING GMBH LOHN JULI 2026 SOWIE SPE SENVERGUETUNG"))
                .isEqualTo("GUTSCHRIFT MUSTER CONSULTING GMBH LOHN");
        // 6 Tokens, Schnitt nach 2: unter der Hälfte → voll.
        assertThat(LookupPatternExtractor.extract("GIRO POST 2025-01 MUSTER AG MIETE"))
                .isEqualTo("GIRO POST 2025-01 MUSTER AG MIETE");
    }

    // --- Invarianten ----------------------------------------------------------------------------

    @Test
    void resultIsAlwaysAPrefixOfTheInput() {
        // Die Eigenschaft, an der die LIKE-Semantik von findMatching hängt.
        String[] inputs = {
            "GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025",
            "GIRO POST MUSTER AG MIETE PER 15. MAI 2025",
            "KAUF/DIENSTLEISTUNG MIGROS M BERN WANKDORF BERN (CH)",
            "LASTSCHRIFT 123456789",
        };
        for (String input : inputs) {
            String pattern = LookupPatternExtractor.extract(input);
            assertThat(input).startsWith(pattern);
            assertThat(pattern).isNotBlank().doesNotEndWith(" ");
        }
    }

    @Test
    void nullAndBlankPassThrough() {
        assertThat(LookupPatternExtractor.extract(null)).isNull();
        assertThat(LookupPatternExtractor.extract("   ")).isEqualTo("   ");
    }
}
