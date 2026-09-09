package com.budgetbuddy.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Die Regel für einen client-gelieferten CHF-Betrag: {@code > 0}, höchstens zwei
 * Nachkommastellen, höchstens {@link #MAX} — und die Normalisierung auf Rappen (BE-FC-04).
 *
 * <p><strong>Warum das hier steht und nicht in den Services.</strong> Die Regel stand nach
 * BE-AUTH-08 zweimal im Backend, in {@code auth/UserService} und {@code budget/FixedCostService},
 * mit derselben Obergrenze aus derselben Ursache. US-07 (Sparziel) und US-14 (Einkommen in den
 * Einstellungen) bringen die nächsten client-gelieferten Beträge; die dritte Kopie war absehbar,
 * und eine Änderung der Spaltenbreite hätte an drei Stellen nachgezogen werden müssen.
 *
 * <p><strong>Warum keine Bean-Validation-Constraint.</strong> Eine Custom-Constraint
 * {@code @ChfAmount} griffe erst, wenn ein Controller {@code @Valid} setzt — der Service bliebe
 * ungeschützt, sobald ihn jemand anders aufruft. Heute ruft nur {@code UserController} das
 * {@code updateIncome} des {@code UserService} auf, und {@code UserIncomePort} gibt als reiner
 * Lese-Port nichts Schreibendes über die Modulgrenze; die Lücke ist also nicht offen, sondern
 * hinge an einer einzigen künftigen Zeile — einem Schreib-Port für US-07/US-14 oder einem
 * zweiten Aufrufer im selben Modul. Eine Regel, die trägt, solange niemand einen zweiten
 * Aufrufer schreibt, ist keine. Das eingebaute {@code @Digits(fraction = 2)} scheiterte
 * zusätzlich an {@code 100.000}: es zählt {@code BigDecimal.scale()} ohne
 * {@code stripTrailingZeros()}.
 *
 * <p><strong>Was hier nicht steht: Meldung und Exception.</strong> {@link #check(BigDecimal)}
 * wirft nichts, sondern meldet, <em>welche</em> Regel verletzt ist. Den Text und den
 * Exception-Typ liefert der aufrufende Service — die Meldungen unterscheiden sich bewusst
 * («Betrag darf …» vs. «Einkommen darf …», US-03 und #148 verlangen feldspezifische Texte), und
 * ein gemeinsamer Exception-Typ wäre der modulübergreifende Zugriff, den CLAUDE.md untersagt.
 *
 * <p>Zustandslos und ohne Spring-Bindung — bewusst kein Bean, damit die Regel auch dort gilt, wo
 * kein Kontext läuft (statische Validierung in den Services, Tests).
 *
 * <p>Der Entscheid für dieses Package ist als Nachtrag «Das {@code money}-Package» in
 * {@code docs/adr/ADR-9-bigdecimal-money.md} festgehalten.
 */
public final class ChfAmounts {

    /** Rappen — Zielskala aller CHF-Beträge nach aussen (ADR-9). */
    public static final int RAPPEN_SCALE = 2;

    /**
     * Kapazitätsgrenze jeder CHF-Spalte: {@code DECIMAL(10,2)} fasst maximal {@code 99999999.99}.
     *
     * <p>Die Zahl stammt aus den Flyway-Migrationen und nirgendwo sonst — {@code monthly_income}
     * in {@code V01__create_users_table.sql}, {@code transactions.betrag} in
     * {@code V02__create_transactions_table.sql}, {@code fixed_costs.betrag} in
     * {@code V03__create_fixed_costs_table.sql} tragen alle dieselbe Spaltendefinition. Wird die
     * Spaltenbreite je geändert, ist das hier die einzige Stelle im Java-Code, die nachzuziehen
     * ist. Ohne diese Prüfung liefe ein grösserer Wert nicht in eine 400-Antwort, sondern in
     * einen DB-Fehler.
     */
    public static final BigDecimal MAX = new BigDecimal("99999999.99");

    /**
     * {@link #MAX} in Schweizer Schreibweise, für die Meldungstexte der Services.
     *
     * <p>Der Text existiert als Konstante, damit die Obergrenze wirklich nur einmal im Code steht.
     * Ohne sie stünde {@code 99'999'999.99} weiterhin in jeder Fehlermeldung erneut — dedupliziert
     * wäre dann die Prüfung, nicht die Zahl.
     */
    public static final String MAX_FORMATTED = "99'999'999.99";

    /**
     * Die verletzbaren Regeln, in der Reihenfolge, in der {@link #check(BigDecimal)} sie prüft.
     *
     * <p>Die Reihenfolge ist Teil des sichtbaren Verhaltens: Eine Eingabe kann mehrere Regeln
     * gleichzeitig verletzen ({@code -0.001} etwa Vorzeichen <em>und</em> Skala), und der User
     * bekommt die Meldung der zuerst geprüften. Sie entspricht der Reihenfolge, die
     * {@code FixedCostService} und {@code UserService} vor BE-FC-04 hatten.
     */
    public enum Violation {
        /** Kein Betrag übermittelt ({@code null}). */
        FEHLT,
        /** Null oder negativ. */
        NICHT_POSITIV,
        /** Mehr als zwei Nachkommastellen — gemessen nach {@code stripTrailingZeros()}. */
        ZU_VIELE_NACHKOMMASTELLEN,
        /** Grösser als {@link #MAX}. */
        UEBER_MAXIMUM
    }

    private ChfAmounts() {}

    /**
     * Prüft einen client-gelieferten Betrag und meldet die erste verletzte Regel.
     *
     * <p>{@code stripTrailingZeros()} vor dem Skala-Vergleich: {@code 100.00} (Skala 2) und
     * {@code 100.000} (Skala 3) sind derselbe Wert, und wie viele Nullen ein Client anhängt, ist
     * seine Sache. Ohne diesen Schritt würde {@code 100.000} fälschlich abgelehnt.
     *
     * @param betrag der zu prüfende Betrag, {@code null} erlaubt (ergibt {@link Violation#FEHLT}).
     * @return die erste verletzte Regel, oder {@link Optional#empty()}, wenn der Betrag gültig ist.
     */
    public static Optional<Violation> check(BigDecimal betrag) {
        if (betrag == null) {
            return Optional.of(Violation.FEHLT);
        }
        if (betrag.signum() <= 0) {
            return Optional.of(Violation.NICHT_POSITIV);
        }
        if (betrag.stripTrailingZeros().scale() > RAPPEN_SCALE) {
            return Optional.of(Violation.ZU_VIELE_NACHKOMMASTELLEN);
        }
        if (betrag.compareTo(MAX) > 0) {
            return Optional.of(Violation.UEBER_MAXIMUM);
        }
        return Optional.empty();
    }

    /**
     * Normalisiert einen bereits geprüften Betrag auf Rappen-Skala.
     *
     * <p>{@link RoundingMode#UNNECESSARY} ist Absicht: An dieser Stelle steht durch
     * {@link #check(BigDecimal)} fest, dass höchstens zwei Nachkommastellen belegt sind. Müsste
     * hier gerundet werden, wäre die Prüfung davor falsch — dann soll es laut scheitern und nicht
     * still runden. Genau das stille Runden ({@code numeric(10,2)} beim Schreiben) war der Defekt
     * aus #148.
     *
     * @param betrag ein Betrag, für den {@link #check(BigDecimal)} bereits
     *     {@link Optional#empty()} geliefert hat.
     * @return denselben Wert mit Skala {@link #RAPPEN_SCALE}.
     * @throws ArithmeticException wenn der Betrag mehr als zwei Nachkommastellen belegt, die
     *     Prüfung also übersprungen wurde.
     * @throws NullPointerException wenn {@code betrag} {@code null} ist.
     */
    public static BigDecimal toRappen(BigDecimal betrag) {
        return betrag.setScale(RAPPEN_SCALE, RoundingMode.UNNECESSARY);
    }
}
