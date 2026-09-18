package com.budgetbuddy.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.budgetbuddy.categorization.CategorizationResult;
import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.categorization.ClaudeCategorizationService;
import com.budgetbuddy.categorization.HybridCategorizationService;
import com.budgetbuddy.support.PostgresTestDatabase;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Der Lerneffekt am Jahresauszug (BE-CAT-13, AC 1 und AC 2): Ein Händler mit variabler Mitteilung
 * kostet einen Claude-Call, nicht zwölf — und die Lookup-Tabelle wächst um eine Zeile je
 * Gegenpartei, nicht je Transaktion.
 *
 * <p>Dieselbe Fixture und dieselbe Bündelung wie in {@link PdfLookupCoverageIntegrationTest},
 * aber in einer eigenen Datenbank: Hier lernt die Tabelle absichtlich, und die 144er-Quote dort
 * hängt an den unveränderten Seeds. Der Claude-Mock antwortet nur für die drei Gegenparteien mit
 * Monatsmitteilung (Miete, Lohn, Steuerrückerstattung) mit einer echten Kategorie — für alles
 * andere mit {@code Sonstiges}, das nach BE-CAT-11 nicht gelernt wird. So bleibt die Messung auf
 * genau die Zeilen beschränkt, um die es in BE-CAT-13 geht.
 *
 * <p><strong>Warum schon der erste Import zählt.</strong> Der {@code ImportJobRunner} lernt pro
 * Bündel. Die Januar-Miete steht im ersten Bündel; ab dem zweiten trifft {@code GIRO POST MUSTER
 * IMMOBILIEN AG MIETE} bereits die Tabelle. Claude sieht die Miete deshalb genau einmal — nicht
 * erst «ab dem zweiten Import» wie im AC, sondern ab dem zweiten Bündel.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PdfLookupLearningIntegrationTest {

    private static final String FIXTURE = "/pdf/Post_Kontoauszug_2025_240_Buchungen.pdf";
    private static final int BATCH_SIZE = 20;

    /** Die zwölf Mieten, das AC-Beispiel aus #321. */
    private static final String RENT = "MUSTER IMMOBILIEN AG MIETE";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "pdf_lookup_learning");
    }

    @Autowired private SwissBankStatementParser parser;
    @Autowired private HybridCategorizationService hybridCategorizationService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private ClaudeCategorizationService claudeCategorizationService;

    /** Alles, was im Lauf an Claude ging — über beide Importe hinweg. */
    private final List<String> sentToClaude = new ArrayList<>();

    @BeforeEach
    void stubClaude() {
        when(claudeCategorizationService.categorizeAll(anyList())).thenAnswer(invocation -> {
            List<String> batch = invocation.getArgument(0);
            sentToClaude.addAll(batch);
            return batch.stream().map(text -> Optional.of(new CategorizationResult(
                    answerFor(text), CategorizationResult.Source.CLAUDE))).toList();
        });
    }

    private static Category answerFor(String text) {
        if (text.contains("IMMOBILIEN")) {
            return Category.WOHNEN;
        }
        if (text.contains("CONSULTING") || text.contains("STEUERVERWALTUNG")) {
            return Category.EINKOMMEN;
        }
        return Category.SONSTIGES;
    }

    @Test
    void aMerchantWithVariableMessageCostsOneClaudeCall_notTwelve() {
        int rowsBefore = countLookupRows();
        List<String> texts = fullTexts();
        assertThat(texts).filteredOn(t -> t.contains(RENT)).hasSize(12);

        // Erster Import: Claude sieht die Miete genau einmal — Bündel 1 lernt, Bündel 2–12 treffen.
        List<Optional<CategorizationResult>> first = importInBatches(texts);
        assertThat(sentToClaude).filteredOn(t -> t.contains(RENT)).hasSize(1);
        assertThat(first).hasSize(240);

        // Zweiter Import desselben Auszugs (AC 1): keine Miete mehr bei Claude, alle zwölf aus
        // der Lookup-Tabelle als Wohnen.
        sentToClaude.clear();
        List<Optional<CategorizationResult>> second = importInBatches(texts);

        assertThat(sentToClaude).noneMatch(t -> t.contains(RENT));
        for (int i = 0; i < texts.size(); i++) {
            if (texts.get(i).contains(RENT)) {
                assertThat(second.get(i)).contains(new CategorizationResult(
                        Category.WOHNEN, CategorizationResult.Source.LOOKUP));
            }
        }

        // AC 2: eine Zeile je Gegenpartei — Miete, Lohn, Steuerrückerstattung —, und der zweite
        // Import hat keine einzige hinzugefügt.
        assertThat(countLookupRows()).isEqualTo(rowsBefore + 3);
        assertThat(countLookupRowsContaining(RENT)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT empfaenger_pattern FROM category_lookup WHERE empfaenger_pattern LIKE ?",
                        String.class, "%" + RENT + "%"))
                .isEqualTo("GIRO POST " + RENT);
    }

    /** Gebündelt wie im {@code ImportJobRunner}: 20er-Scheiben, jede durch die ganze Kette. */
    private List<Optional<CategorizationResult>> importInBatches(List<String> texts) {
        List<Optional<CategorizationResult>> results = new ArrayList<>();
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            int to = Math.min(from + BATCH_SIZE, texts.size());
            results.addAll(hybridCategorizationService.categorizeAll(texts.subList(from, to)));
        }
        return results;
    }

    private List<String> fullTexts() {
        return parser.parse(fixture(FIXTURE)).stream().map(ParsedTransaction::fullText).toList();
    }

    private int countLookupRows() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM category_lookup", Integer.class);
    }

    private int countLookupRowsContaining(String fragment) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup WHERE empfaenger_pattern LIKE ?",
                Integer.class, "%" + fragment + "%");
    }

    private static byte[] fixture(String resource) {
        try (InputStream in = PdfLookupLearningIntegrationTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Fixture nicht im Classpath: " + resource);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
