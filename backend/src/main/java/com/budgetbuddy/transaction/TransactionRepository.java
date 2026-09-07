package com.budgetbuddy.transaction;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository-Zugriff auf {@link Transaction} (transaction-intern, kein modulübergreifender Zugriff).
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Lädt alle <em>Ausgaben</em> ({@code is_income = false}) eines Users, deren Buchungsdatum in
     * das Intervall {@code [von, bis]} fällt — beide Grenzen inklusive. Für das Kategorie-Summary
     * (BE-CAT-05) werden die Monatsgrenzen im Service berechnet und die Aggregation in Java mit
     * {@link java.math.BigDecimal} vorgenommen — dieselbe Begründung wie in
     * {@code FixedCostRepository}: ADR-9 wird an einer Stelle durchgesetzt statt an zweien.
     */
    List<Transaction> findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
            Long userId, LocalDate von, LocalDate bis);

    /**
     * Seitenweise Variante für die Transaktionsliste (FE-CAT-05, US-13): dieselbe Auswahl wie oben,
     * aber begrenzt auf das Fenster aus {@code pageable}. Sortiert wird über dessen {@code Sort} —
     * die Reihenfolge gehört in die Query, weil ein späteres Sortieren in Java nur die
     * <em>geladene</em> Seite ordnen könnte und damit die falschen Zeilen lieferte.
     *
     * <p>{@link Slice} statt {@code Page}: Spring Data holt intern {@code size + 1} Zeilen und
     * beantwortet {@code hasNext()} daraus. Eine Gesamtzahl braucht die Anzeige nicht, und sie zu
     * liefern hiesse, neben jeder Seite eine zweite Query über den ganzen Monat laufen zu lassen.
     */
    Slice<Transaction> findByUserIdAndIncomeFalseAndBuchungsdatumBetween(
            Long userId, LocalDate von, LocalDate bis, Pageable pageable);

    /**
     * Wie oben, zusätzlich eingegrenzt auf ein Kategorie-Label (FE-CAT-05, US-13).
     *
     * <p>Das {@code coalesce} bildet dieselbe Regel ab wie {@code TransactionListService.labelOf()}
     * auf dem Antwortpfad: eine noch nicht kategorisierte Buchung ({@code category IS NULL}) zählt
     * als <em>Sonstiges</em>, weil die Kategorie-Übersicht sie unter diesem Namen summiert — ein
     * Filter auf {@code Sonstiges} muss sie deshalb treffen. Die Regel steht damit an zwei Stellen;
     * das Label selbst kommt an beiden aus {@code Category.SONSTIGES.getLabel()} und wird hier als
     * Parameter hereingereicht, damit kein zweiter String entsteht.
     *
     * <p>Der Filter kann die Mandantentrennung nicht aufweichen: {@code t.userId = :userId} steht
     * unabhängig davon in der Query.
     *
     * <p>Ohne {@code order by} im JPQL — die Reihenfolge kommt aus dem {@code Sort} des
     * {@code pageable}, sonst stünden zwei Sortierungen nebeneinander.
     */
    @Query("""
            select t from Transaction t
             where t.userId = :userId
               and t.income = false
               and t.buchungsdatum between :von and :bis
               and coalesce(t.category, :sonstiges) = :label
            """)
    Slice<Transaction> findExpensesByCategoryLabel(
            @Param("userId") Long userId,
            @Param("von") LocalDate von,
            @Param("bis") LocalDate bis,
            @Param("label") String label,
            @Param("sonstiges") String sonstiges,
            Pageable pageable);

    /**
     * Die Monate, in denen dieser User Ausgaben hat — als Paare {@code [jahr, monat]}, absteigend
     * (FE-CAT-04, US-12). Eingabe des Monats-Dropdowns, das damit nur Monate anbietet, in denen
     * auch etwas zu sehen ist.
     *
     * <p>Aggregiert in der Datenbank statt in Java: die Alternative wäre, alle Buchungsdaten eines
     * Users zu laden, um daraus eine Handvoll Monate zu destillieren — ein Vollload über die ganze
     * Historie für eine Liste, die selten mehr als ein paar Dutzend Einträge hat.
     *
     * <p>Über {@code year()}/{@code month()} statt über ein datenbankspezifisches
     * {@code to_char(…, 'YYYY-MM')}: die Formatierung passiert in
     * {@link TransactionListService#availableMonths(long)} mit {@link java.time.YearMonth}, also
     * mit derselben Klasse, die {@link MonthParser} beim Lesen des Parameters verwendet. Format und
     * Parsing können so nicht auseinanderlaufen.
     *
     * <p>Nur Ausgaben, wie im Summary und in der Liste: ein Monat mit ausschliesslich Gutschriften
     * hätte in der Kategorie-Übersicht nichts anzuzeigen und gehört nicht ins Dropdown.
     *
     * @return Paare {@code [Integer jahr, Integer monat]}, absteigend nach Jahr und Monat.
     */
    @Query("""
            select distinct year(t.buchungsdatum), month(t.buchungsdatum)
              from Transaction t
             where t.userId = :userId
               and t.income = false
             order by year(t.buchungsdatum) desc, month(t.buchungsdatum) desc
            """)
    List<Object[]> findDistinctExpenseMonths(@Param("userId") Long userId);

    /**
     * Lädt alle <em>Gutschriften</em> ({@code is_income = true}) eines Users, deren Buchungsdatum in
     * das Intervall {@code [von, bis]} fällt — beide Grenzen inklusive. Eingabe der
     * Einkommens-Heuristik (BE-STS-02, US-06), die daraus wiederkehrende Zahlungseingänge ableitet.
     *
     * <p>Spiegelbild zu {@link #findByUserIdAndIncomeFalseAndBuchungsdatumBetween}: die Auswertung
     * läuft in Java mit {@link java.math.BigDecimal}, nicht per Aggregat in SQL. Dieselbe Begründung
     * wie dort — ADR-9 wird an einer Stelle durchgesetzt statt an zweien; hier kommt hinzu, dass die
     * Gruppierung über den normalisierten Buchungstext ohnehin nicht in eine Query passt.
     */
    List<Transaction> findByUserIdAndIncomeTrueAndBuchungsdatumBetween(
            Long userId, LocalDate von, LocalDate bis);

    /**
     * Die Buchungen eines Monats, deren Richtung der Parser nur angenommen hat (BE-PDF-10, US-04) —
     * absteigend nach Buchungsdatum, bei Gleichstand nach ID, wie die Transaktionsliste.
     *
     * <p>Ohne Einschränkung auf {@code is_income}: Ein unsicher markierter Eintrag ist zwar immer
     * als Belastung importiert worden (das ist der konservative Default), aber diese Auswahl darf
     * nicht davon abhängen. Sonst verschwände eine Buchung aus der Prüfliste, sobald sie jemand auf
     * Gutschrift setzt — und genau dann fällt auch das Flag, das sie hier überhaupt erst
     * hereinbringt. Zwei Bedingungen für dieselbe Aussage laufen irgendwann auseinander.
     *
     * <p>Ungepaginiert, anders als die Ausgabenliste (FE-CAT-05): Das hier ist eine Aufgabenliste,
     * die der Nutzer abarbeitet und die dabei schrumpft, keine Historie zum Blättern. Die
     * Obergrenze ist die Zahl der Buchungen eines Monats — dieselbe Menge, die
     * {@code TransactionSummaryService} für dieselbe Seite ohnehin lädt.
     */
    List<Transaction> findByUserIdAndDirectionUncertainTrueAndBuchungsdatumBetweenOrderByBuchungsdatumDescIdDesc(
            Long userId, LocalDate von, LocalDate bis);

    /**
     * Duplikatcheck des PDF-Imports (BE-PDF-02): {@code true}, wenn dieser User bereits
     * Transaktionen aus dem PDF mit diesem SHA-256 importiert hat. Pro User — dasselbe PDF darf
     * von einem anderen User (z. B. Gemeinschaftskonto) erneut importiert werden.
     */
    boolean existsByUserIdAndPdfSha256(Long userId, String pdfSha256);

    /**
     * Löscht alle Transaktionen dieses Users aus dem PDF mit diesem SHA-256 (FE-PDF-03): der
     * bestätigte Force-Import <em>ersetzt</em> den vorherigen Import, statt seine Buchungen ein
     * zweites Mal danebenzulegen. Wie beim Duplikatcheck ist die Einschränkung auf {@code userId}
     * die Mandantentrennung — ohne sie würde der Hash quer über alle User löschen.
     *
     * <p>Manuelle Kategorie-Korrekturen gehen dabei nicht verloren: die leben als Lookup-Pattern
     * in {@code category_lookup} ({@link TransactionCategoryService}) und greifen beim erneuten
     * Kategorisieren wieder.
     *
     * @return Anzahl gelöschter Zeilen.
     */
    long deleteByUserIdAndPdfSha256(Long userId, String pdfSha256);

    /**
     * Löscht alle Transaktionen eines Users (Kontolöschung, US-02, DB-07).
     *
     * <p>Bewusst {@code @Modifying} statt einer abgeleiteten {@code deleteByUserId}-Methode:
     * Letztere lädt die Entities und ruft {@code remove()} auf, was Hibernate bis zum Flush
     * aufschiebt. Der Aufrufer ({@code UserService.deleteUser}) löscht danach den User selbst —
     * dessen Fremdschlüssel-Constraint verlangt, dass diese Zeilen zu dem Zeitpunkt bereits
     * physisch entfernt sind, nicht erst am Ende der Transaktion.
     */
    @Modifying
    @Query("delete from Transaction t where t.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
