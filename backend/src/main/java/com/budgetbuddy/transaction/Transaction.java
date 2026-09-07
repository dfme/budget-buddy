package com.budgetbuddy.transaction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA-Entity der {@code transactions}-Tabelle (Flyway V02, DB-02).
 *
 * <p>Schema-treues Mapping: {@code betrag} ist eine positive Magnitude als {@link BigDecimal}
 * (niemals {@code double}/{@code float}, ADR-9); die Richtung steht in {@code isIncome}
 * ({@code true} = Gutschrift/Einkommen, {@code false} = Belastung/Ausgabe). {@code category}
 * bleibt {@code null}, bis die Transaktion kategorisiert wurde (US-05).
 *
 * <p>Erster Konsument der persistierten Transaktionen ist BE-CAT-05 (Kategorie-Summary). Der
 * schreibende Pfad (BE-PDF-02, PdfImportService) ergänzt später Konstruktor/Factory nach Bedarf.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDate buchungsdatum;

    @Column(nullable = false)
    private String buchungstext;

    /**
     * Die eingerückten Fortsetzungszeilen der Buchung, mit {@code \n} verbunden (BE-PDF-07, V06).
     *
     * <p>{@code null} für alles, was vor BE-PDF-07 importiert wurde, und für Buchungen ohne
     * Detailzeilen — siehe {@link #getBuchungsdetails()}.
     */
    @Column(name = "buchungsdetails")
    private String buchungsdetails;

    @Column(nullable = false)
    private BigDecimal betrag;

    @Column(name = "is_income", nullable = false)
    private boolean income;

    /**
     * {@code true}, wenn der Parser die Richtung dieser Buchung nicht eindeutig bestimmen konnte
     * und sie konservativ als Belastung übernommen hat (BE-PDF-10, V08).
     *
     * <p>Der Wert ist ein Hinweis an die Oberfläche, nicht an die Berechnung: {@link #income} ist
     * in diesem Fall eine Annahme, kein Befund. Sobald der Nutzer entschieden hat, fällt das Flag
     * — siehe {@link #correctDirection(boolean)}.
     */
    @Column(name = "direction_uncertain", nullable = false)
    private boolean directionUncertain;

    @Column
    private String category;

    @Column(name = "pdf_sha256")
    private String pdfSha256;

    protected Transaction() {
        // JPA
    }

    /**
     * Erzeugt eine zu persistierende Transaktion (schreibender Pfad, z. B. BE-PDF-02).
     *
     * @param userId ID des besitzenden Users.
     * @param buchungsdatum Buchungsdatum.
     * @param buchungstext Text der Buchungszeile. Bei Kartenzahlungen der Händler, bei
     *     Überweisungen nur der Buchungs<em>typ</em> — der Empfänger steht dann in
     *     {@code buchungsdetails}.
     * @param buchungsdetails Detailzeilen mit {@code \n} verbunden, oder {@code null}
     *     (BE-PDF-07). Aus {@code ParsedTransaction.detailsAsText()}.
     * @param betrag positive Magnitude in CHF ({@link BigDecimal}, ADR-9). Richtung via
     *     {@code income}.
     * @param income {@code true} für Gutschriften (Einkommen), {@code false} für Belastungen.
     * @param category Kategorie-Label oder {@code null}, solange nicht kategorisiert.
     * @param pdfSha256 SHA-256 des Quell-PDFs oder {@code null}.
     */
    public Transaction(Long userId, LocalDate buchungsdatum, String buchungstext,
            String buchungsdetails, BigDecimal betrag, boolean income, String category,
            String pdfSha256) {
        this(userId, buchungsdatum, buchungstext, buchungsdetails, betrag, income, false, category,
                pdfSha256);
    }

    /**
     * Wie oben, zusätzlich mit dem Richtungs-Flag aus dem Parser (BE-PDF-10).
     *
     * <p>Getrennt gehalten statt als einziger Konstruktor: {@code directionUncertain} kommt
     * ausschliesslich aus {@code SwissBankStatementParser} über {@code ParsedTransaction}. Jeder
     * andere Erzeuger einer Transaktion hat keine Meinung dazu und soll sie auch nicht ausdrücken
     * müssen — für ihn ist die Richtung schlicht bekannt.
     *
     * @param directionUncertain {@code true}, wenn die Richtung geraten ist — siehe
     *     {@link #isDirectionUncertain()}.
     */
    public Transaction(Long userId, LocalDate buchungsdatum, String buchungstext,
            String buchungsdetails, BigDecimal betrag, boolean income, boolean directionUncertain,
            String category, String pdfSha256) {
        this.userId = userId;
        this.buchungsdatum = buchungsdatum;
        this.buchungstext = buchungstext;
        this.buchungsdetails = buchungsdetails;
        this.betrag = betrag;
        this.income = income;
        this.directionUncertain = directionUncertain;
        this.category = category;
        this.pdfSha256 = pdfSha256;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getBuchungsdatum() {
        return buchungsdatum;
    }

    public String getBuchungstext() {
        return buchungstext;
    }

    /**
     * Die Detailzeilen der Buchung — Gegenpartei und Verwendungszweck —, mit {@code \n} verbunden,
     * oder {@code null} (BE-PDF-07).
     *
     * <p>{@code null} hat zwei Bedeutungen, die sich hier bewusst nicht unterscheiden lassen: Die
     * Buchung hatte keine Detailzeilen, oder sie wurde vor BE-PDF-07 importiert. Ein Backfill ist
     * ausgeschlossen — die Zeilen stehen nur im Quell-PDF, und davon wird nur der
     * {@link #getPdfSha256() SHA-256} gespeichert, nicht die Datei. Aufrufer dürfen aus
     * {@code null} deshalb nie schliessen, dass es keine Gegenpartei gab.
     */
    public String getBuchungsdetails() {
        return buchungsdetails;
    }

    /** Positive Magnitude des Betrags (Skala 2). Richtung siehe {@link #isIncome()}. */
    public BigDecimal getBetrag() {
        return betrag;
    }

    /** {@code true} für Gutschriften (Einkommen), {@code false} für Belastungen (Ausgaben). */
    public boolean isIncome() {
        return income;
    }

    /**
     * {@code true}, solange {@link #isIncome()} eine Annahme des Parsers ist und kein Befund
     * (BE-PDF-10, US-04).
     *
     * <p>Der Parser leitet die Richtung aus dem Saldo ab. Gelingt das nicht — mehrdeutiges
     * Saldo-Delta, fehlender Anfangssaldo, zu grosser Buchungsblock —, übernimmt er die Buchung
     * konservativ als Belastung und setzt dieses Flag. Es ist damit kein Fehlerzustand, sondern
     * eine offene Frage an den Nutzer: Ist eine Gutschrift darunter, ist ihr Vorzeichen gedreht
     * und Safe-to-Spend fällt zu tief aus.
     *
     * <p>Für Zeilen, die vor V08 importiert wurden, ist der Wert {@code false} — nicht, weil ihre
     * Richtung geprüft wäre, sondern weil es die Spalte damals nicht gab. Die Begründung, warum
     * kein Backfill stattfindet, steht in der Migration.
     */
    public boolean isDirectionUncertain() {
        return directionUncertain;
    }

    /**
     * Übernimmt die vom Nutzer entschiedene Buchungsrichtung (BE-PDF-10, US-04).
     *
     * <p>Setzt die Richtung <em>und</em> räumt {@link #isDirectionUncertain()} ab — auch dann,
     * wenn der Nutzer die angenommene Richtung bloss bestätigt. Bestätigen ist eine Entscheidung:
     * Die Buchung bleibt eine Belastung, aber sie ist es ab jetzt nachweislich und nicht mehr
     * mangels Alternative.
     *
     * <p>Bewusst eine Methode statt zweier Setter. Die beiden Felder gehören zusammen — ein
     * {@code setIncome()} ohne das Abräumen des Flags liesse eine korrigierte Buchung für immer
     * in der Prüfliste stehen, und ein Aufrufer, der es vergisst, fiele nirgends auf.
     *
     * @param income {@code true} für Gutschrift, {@code false} für Belastung.
     */
    public void correctDirection(boolean income) {
        this.income = income;
        this.directionUncertain = false;
    }

    /** Kategorie-Label wie in {@link com.budgetbuddy.categorization.Category}, oder {@code null}. */
    public String getCategory() {
        return category;
    }

    /**
     * Setzt das Kategorie-Label (manuelle Korrektur, BE-CAT-04). Erwartet einen Wert aus
     * {@link com.budgetbuddy.categorization.Category}; die Validierung erfolgt beim Aufrufer.
     */
    public void setCategory(String category) {
        this.category = category;
    }

    public String getPdfSha256() {
        return pdfSha256;
    }
}
