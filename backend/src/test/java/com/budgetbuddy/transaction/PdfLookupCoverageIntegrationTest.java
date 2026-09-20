package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.categorization.ClaudeCategorizationService;
import com.budgetbuddy.categorization.HybridCategorizationService;
import com.budgetbuddy.categorization.LookupTableService;
import com.budgetbuddy.support.PostgresTestDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Misst das Verhältnis, auf dem ADR-6 beruht: Wie viel eines realistischen Jahresauszugs erledigt
 * die Lookup-Tabelle gratis, und wie viel muss an die Claude-API?
 *
 * <p>Die Zahl stand bisher nur als Schätzung in ADR-6 («~70–80%») und als Logzeile im
 * {@link ImportJobRunner}. Geprüft war sie nirgends — die vorhandenen Fixtures sind zu klein und zu
 * händlerlastig, um eine Quote überhaupt aussagekräftig zu machen. {@code
 * Post_Kontoauszug_2025_240_Buchungen.pdf} ist für genau diese Messung gebaut: 240 Buchungen über
 * ein Kalenderjahr, davon 66.25% mit Lookup-Treffer. Das liegt bewusst <em>unter</em> der Annahme
 * von ADR-6, damit die Claude-Stufe spürbar Last bekommt statt fast leer zu laufen.
 *
 * <p>Der Test greift auf die echten Flyway-Seeds zu — {@code V04} plus die Ergänzung {@code V15}
 * für Bargeldbezug und Steuern. Ändern sich die Seeds, ändert sich die Quote: Das Generator-Skript
 * rechnet sie beim Erzeugen gegen dieselben Migrationen und bricht ausserhalb des Zielbands ab,
 * dieser Test hält sie danach fest.
 *
 * <p><strong>Warum hier exakt gepinnt wird und im Generator nur ein Band gilt.</strong> Die
 * Toleranz von ±5 Prozentpunkten im Skript ist für den Fall gedacht, dass jemand die Fixture neu
 * erzeugt, nachdem sich die Seeds geändert haben — sie soll dann nicht wegen eines einzelnen
 * neuen Händlers scheitern. Dieser Test läuft gegen die <em>eingecheckte</em> Fixture, die sich
 * nicht mitbewegt: Kommt ein Seed dazu, der einen ihrer Texte trifft, ist die Zahl schlicht
 * falsch geworden und soll auffallen. Ein neuer Seed-Eintrag lässt diesen Test also absichtlich
 * reissen; die Antwort darauf ist, die Zahl hier nachzuziehen — nicht, die Assertion
 * aufzuweichen.
 *
 * <p><strong>Der Fall ist mit BE-CAT-17 eingetreten.</strong> {@code V15} seedet Bargeldbezug und
 * Steuern; 15 Buchungen wechseln dadurch von Claude auf den Lookup — zwölfmal {@code BARBEZUG
 * POSTOMAT BAHNHOF BERN} und dreimal die Steuerrückerstattung (April, August, Dezember). Aus 144
 * wurden 159. Die <em>Fixture</em> blieb dabei unverändert, und zwar nicht aus Bequemlichkeit: Die
 * Seeds gehen nie in die gedruckte Seite ein, {@code via_lookup} liest das Generator-Skript
 * ausschliesslich für seine Bandprüfung und den Report. Ein Neulauf hätte allein den
 * PDF-Zeitstempel geändert.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfLookupCoverageIntegrationTest {

    private static final String FIXTURE = "/pdf/Post_Kontoauszug_2025_240_Buchungen.pdf";

    /** Der layouttreue Auszug — dieselben Seeds, aber das echte PostFinance-Satzbild. */
    private static final String LAYOUT_FIXTURE = "/pdf/Post_Kontoauszug_2026_Juli_20_Buchungen.pdf";

    private static final int TRANSACTION_COUNT = 240;
    private static final int EXPECTED_LOOKUP_HITS = 159; // 66.25%, seit V15 (BE-CAT-17): vorher 144
    private static final int EXPECTED_CLAUDE_CALLS = TRANSACTION_COUNT - EXPECTED_LOOKUP_HITS;

    /** Die gebaute Quote des Jahresauszugs — Vergleichsmass für den layouttreuen Auszug. */
    private static final double POST_YEAR_LOOKUP_SHARE = 0.6625;

    /** {@code budgetbuddy.import.batch-size} — hier gespiegelt wie im ImportJobRunner (ADR-14). */
    private static final int BATCH_SIZE = 20;

    /**
     * Beliebiger User (BE-CAT-12): Die Quote misst nur die globalen Seeds; gelernte Patterns hat
     * dieser User keine, und die Claude-Attrappe liefert {@code Sonstiges}, das nie gelernt wird —
     * die Tabelle {@code user_category_lookup} bleibt in diesem Test leer.
     */
    private static final long USER_ID = 1L;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "pdf_lookup_coverage");
    }

    @Autowired private SwissBankStatementParser parser;
    @Autowired private LookupTableService lookupTableService;
    @Autowired private HybridCategorizationService hybridCategorizationService;

    /** Nur die zweite Stufe wird ersetzt: kein API-Key in CI, keine Kosten, keine Netzabhängigkeit. */
    @MockitoBean private ClaudeCategorizationService claudeCategorizationService;

    @Test
    void twoThirdsOfTheYearAreCoveredByTheLookupTable() {
        List<String> texts = fullTexts();

        assertThat(texts).hasSize(TRANSACTION_COUNT);
        long hits = texts.stream().filter(t -> lookupTableService.categorize(USER_ID, t).isPresent()).count();

        assertThat(hits).isEqualTo(EXPECTED_LOOKUP_HITS);
        assertThat((double) hits / TRANSACTION_COUNT).isEqualTo(POST_YEAR_LOOKUP_SHARE);
    }

    @Test
    void knownMerchantsHit_transferAndSalaryBookingsDoNot() {
        // Die beiden Seiten der Quote an Beispielen — sonst sagt «159» nicht, ob die richtigen
        // Buchungen getroffen wurden.
        assertThat(lookupTableService.categorize(USER_ID, "KAUF/DIENSTLEISTUNG MIGROS M BERN WANKDORF"))
                .map(CategorizationResult::category)
                .contains(Category.LEBENSMITTEL);
        assertThat(lookupTableService.categorize(USER_ID, "LASTSCHRIFT CSS VERSICHERUNG AG PRAEMIE MAI 2025"))
                .map(CategorizationResult::category)
                .contains(Category.VERSICHERUNG);
        assertThat(lookupTableService.categorize(USER_ID, "LASTSCHRIFT SALT MOBILE SA"))
                .map(CategorizationResult::category)
                .contains(Category.TELEKOM);

        // Die 15 Buchungen, die V15 (BE-CAT-17) von Claude auf den Lookup holt — im Wortlaut der
        // Fixture, nicht im Wortlaut der Seeds. Ohne diese Zeilen belegte nur die Zahl 159, dass
        // sich etwas bewegt hat, aber nicht, dass sich das Richtige bewegt hat.
        assertThat(lookupTableService.categorize(USER_ID, "BARBEZUG POSTOMAT BAHNHOF BERN"))
                .map(CategorizationResult::category)
                .contains(Category.BARGELDBEZUG);
        assertThat(lookupTableService.categorize(
                        USER_ID, "GUTSCHRIFT STEUERVERWALTUNG KT. BERN RUECKERSTATTUNG APRIL 2025"))
                .map(CategorizationResult::category)
                .contains(Category.STEUERN);

        // Der Wortlaut der anderen Banken, den die 240er-Fixture nicht trägt: UBS und Raiffeisen
        // buchen «Bezug … Bancomat» (generate_pdf_fixtures.py:842, :1048), die Demo-Auszüge
        // «BARGELDBEZUG» (generate_demo_statements.py:150).
        assertThat(lookupTableService.categorize(USER_ID, "Bezug UBS Bancomat"))
                .map(CategorizationResult::category)
                .contains(Category.BARGELDBEZUG);
        assertThat(lookupTableService.categorize(USER_ID, "BARGELDBEZUG BANCOMAT BERN BAHNHOF"))
                .map(CategorizationResult::category)
                .contains(Category.BARGELDBEZUG);

        // Gegenprobe zur Auslassung von 'ATM' und 'STEUERN' in V15: Beides sind Substrings
        // gewöhnlicher Buchungstexte, und das Matching kennt seit BE-CAT-14 keine Wortgrenzen.
        assertThat(lookupTableService.categorize(USER_ID, "KAUF/DIENSTLEISTUNG BAR ATMOSPHERE BERN")).isEmpty();
        assertThat(lookupTableService.categorize(USER_ID, "LASTSCHRIFT MUSTER STEUERBERATUNG AG")).isEmpty();

        // Genau die Buchungen, für die es die zweite Stufe gibt: Lohn, Miete, Strom, Gebühren —
        // in der Lookup-Tabelle steht kein Pattern, das darauf passt.
        assertThat(lookupTableService.categorize(USER_ID, "GUTSCHRIFT LOHN MAI Muster Consulting GmbH")).isEmpty();
        assertThat(lookupTableService.categorize(USER_ID, "GIRO POST Muster Immobilien AG MIETE MAI 2025")).isEmpty();
        assertThat(lookupTableService.categorize(USER_ID, "ESR Stadtwerke Bern")).isEmpty();
        assertThat(lookupTableService.categorize(USER_ID, "LASTSCHRIFT SERAFE AG RADIO UND TV")).isEmpty();
    }

    @Test
    void theRemainingThirdReachesClaude_andNothingElseDoes() {
        when(claudeCategorizationService.categorizeAll(anyList())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            return Collections.nCopies(batch.size(), Optional.of(
                    new CategorizationResult(Category.SONSTIGES, CategorizationResult.Source.CLAUDE)));
        });

        List<String> texts = fullTexts();
        // Gebündelt wie im ImportJobRunner: erst in 20er-Scheiben, dann pro Scheibe durch die Kette.
        List<Optional<CategorizationResult>> results = new ArrayList<>();
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, texts.size());
            results.addAll(hybridCategorizationService.categorizeAll(USER_ID, texts.subList(from, to)));
        }

        assertThat(results).hasSize(TRANSACTION_COUNT);
        assertThat(results).allSatisfy(r -> assertThat(r).isPresent());

        ArgumentCaptor<List<String>> sent = ArgumentCaptor.forClass(List.class);
        verify(claudeCategorizationService, org.mockito.Mockito.atLeastOnce())
                .categorizeAll(sent.capture());

        List<String> toClaude = sent.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(toClaude).hasSize(EXPECTED_CLAUDE_CALLS);
        // Kein Bündel überschreitet die Bündelgrösse — die Grenze, an der die Laufzeit hängt.
        assertThat(sent.getAllValues()).allSatisfy(b -> assertThat(b).hasSizeLessThanOrEqualTo(BATCH_SIZE));
        // Und nichts, was der Lookup gekonnt hätte, landet trotzdem beim (kostenpflichtigen) Call.
        assertThat(toClaude).allSatisfy(t -> assertThat(lookupTableService.categorize(USER_ID, t)).isEmpty());

        // Die Zahl, um die es geht: 81 unbekannte Transaktionen kosten in 20er-Bündeln 12
        // Requests statt 81 sequentieller Einzel-Calls (ADR-14, #192). Die Bündelzahl hat V15
        // nicht bewegt: Kein 20er-Schnitt läuft leer, weil jeder Monat weiterhin mindestens
        // sechs unbekannte Buchungen trägt.
        assertThat(sent.getAllValues()).hasSize(12);
    }

    /**
     * Dieselbe Messung am layouttreuen Auszug — und sie fällt deutlich schlechter aus.
     *
     * <p>Der 240er-Auszug schreibt den Händler in die Buchungszeile und kommt so auf 60%. Echte
     * PostFinance-Auszüge schreiben dort die Zahlungsart; der Händler steht in den Detailzeilen,
     * hinter Kartennummer, IBAN und Anschrift. Damit hängt jeder Treffer daran, dass die
     * sprechende Zeile den Weg durch das Rauschen und durch {@code MAX_DETAIL_LINES} überlebt.
     *
     * <p>Der Test hält die Differenz fest, nicht einen Zielwert: 20 Buchungen sind keine
     * belastbare Quote. Die Aussage ist, dass das Satzbild die Trefferrate stärker bestimmt als
     * die Länge der Seed-Liste — und dass ADR-6s 70–80% für dieses Layout zu optimistisch sind.
     */
    @Test
    void theSameSeedsScoreLowerOnTheRealisticLayout() {
        List<String> texts = fullTexts(LAYOUT_FIXTURE);

        assertThat(texts).hasSize(20);
        long hits = texts.stream().filter(t -> lookupTableService.categorize(USER_ID, t).isPresent()).count();

        assertThat(hits).isEqualTo(7);
        assertThat((double) hits / texts.size()).isLessThan(POST_YEAR_LOOKUP_SHARE);

        // Alle sieben Treffer stammen aus einer Detailzeile, keiner aus der Buchungszeile.
        assertThat(texts)
                .filteredOn(t -> lookupTableService.categorize(USER_ID, t).isPresent())
                .allSatisfy(
                        t -> assertThat(lookupTableService.categorize(USER_ID, t.split(" ")[0])).isEmpty());
    }

    private List<String> fullTexts() {
        return fullTexts(FIXTURE);
    }

    private List<String> fullTexts(String resource) {
        return parser.parse(fixture(resource)).stream().map(ParsedTransaction::fullText).toList();
    }

    private static byte[] fixture(String resource) {
        try (InputStream in = PdfLookupCoverageIntegrationTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
