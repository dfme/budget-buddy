package com.budgetbuddy.recurring;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;

/**
 * JPA-Entity der {@code recurring_expenses}-Tabelle (Flyway V11, DB-09).
 *
 * <p>Eine erkannte wiederkehrende Ausgabe eines Users (US-08): ein Empfänger, der in mindestens
 * zwei aufeinanderfolgenden Monaten einen ähnlichen Betrag belastet hat. Pro User und
 * {@code payeeKey} gibt es genau eine Zeile ({@code UNIQUE (user_id, payee_key)}) — so bleibt ein
 * «Kein Abo» ({@link RecurringExpenseStatus#DISMISSED}) dauerhaft, weil die Erkennung daneben keine
 * zweite Zeile anlegen kann.
 *
 * <p><strong>Die Zeile wird seit BE-REC-04 (#350) bei jedem Erkennungslauf neu bewertet</strong>
 * (V16): {@link #updateFrom} zieht Betrag und Erstmonat auf das jüngste qualifizierende Paar nach,
 * {@link #markEnded()} und {@link #markActive()} schalten zwischen {@code DETECTED} und
 * {@link RecurringExpenseStatus#ENDED}. Vorher war sie nach der ersten Erkennung unveränderlich,
 * ausser durch {@link #dismiss()}.
 *
 * <p>{@code payeeKey} steht ausschliesslich in Grossschreibung — der Vertrag aus V11, den der
 * {@link RecurringExpenseService} beim Schreiben durchsetzt.
 *
 * <p>{@code firstDetectedMonth} ist der <em>erste Monat der Abo-Reihe in den Daten</em>, nicht der
 * Monat des Erkennungslaufs: Letzteren hält {@code createdAt} bereits fest. Gespeichert als
 * {@code YYYY-MM}-Text, wie die Spalte es vorsieht; {@link YearMonth#toString()} und
 * {@link YearMonth#parse(CharSequence)} sind für dieses Format zueinander invers.
 *
 * <p>{@code notificationId} zeigt FK-los auf die Benachrichtigung, die diesen Eintrag zusammen
 * mit den anderen desselben Erkennungslaufs gemeldet hat (V14, FE-NOTIF-04). Aus ihrem
 * Gelesen-Zustand leitet {@code RecurringExpenseService.list} das «Neu»-Flag ab. {@code null}
 * für Zeilen ohne Bündel — die gelten nie als neu.
 *
 * <p>{@code amount} ist {@link BigDecimal} (ADR-9) — nie {@code double}/{@code float}. Er trägt den
 * Betrag des <em>jüngsten</em> qualifizierenden Monatspaars, nicht den der ersten Erkennung.
 */
@Entity
@Table(name = "recurring_expenses")
public class RecurringExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "payee_key", nullable = false)
    private String payeeKey;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecurringExpenseStatus status;

    @Column(name = "first_detected_month", nullable = false)
    private String firstDetectedMonth;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "notification_id")
    private Long notificationId;

    protected RecurringExpense() {
        // JPA
    }

    /**
     * Legt einen neu erkannten Eintrag mit Status {@link RecurringExpenseStatus#DETECTED} an.
     *
     * @param userId ID des besitzenden Users.
     * @param payeeKey normalisierter Empfänger in Grossschreibung.
     * @param amount Betrag der jüngsten erkannten Belastung, Skala 2.
     * @param firstDetectedMonth erster Monat der Abo-Reihe in den Daten.
     * @param createdAt Zeitpunkt des Erkennungslaufs aus der injizierten {@code Clock}.
     * @param notificationId ID der Bündel-Benachrichtigung dieses Erkennungslaufs.
     */
    public RecurringExpense(Long userId, String payeeKey, BigDecimal amount,
            YearMonth firstDetectedMonth, Instant createdAt, Long notificationId) {
        this.userId = userId;
        this.payeeKey = payeeKey;
        this.amount = amount;
        this.status = RecurringExpenseStatus.DETECTED;
        this.firstDetectedMonth = firstDetectedMonth.toString();
        this.createdAt = createdAt;
        this.notificationId = notificationId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getPayeeKey() {
        return payeeKey;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public RecurringExpenseStatus getStatus() {
        return status;
    }

    public YearMonth getFirstDetectedMonth() {
        return YearMonth.parse(firstDetectedMonth);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getNotificationId() {
        return notificationId;
    }

    /**
     * Markiert den Eintrag als «Kein Abo» (BE-REC-02, US-08).
     *
     * <p>Idempotent, analog {@code Notification#markRead}: ein zweiter Aufruf auf einen bereits
     * {@link RecurringExpenseStatus#DISMISSED}-Eintrag ändert nichts. Aus
     * {@link RecurringExpenseStatus#ENDED} heraus lässt die Methode den Übergang zu, die Übersicht
     * bietet ihn aber bewusst nicht an: «Kein Abo» sagt «war nie ein Abo», ein ausgelaufener
     * Eintrag dagegen «läuft nicht mehr» ({@code recurring-expense-list.ts}, Abschnitt
     * «Beendet»). Wer einen solchen Empfänger dauerhaft ausschliessen will, verneint ihn, sobald
     * er wieder als {@code DETECTED} erscheint — bis dahin mindert er ohnehin nichts.
     */
    public void dismiss() {
        this.status = RecurringExpenseStatus.DISMISSED;
    }

    /**
     * Zieht Betrag und Erstmonat auf das Ergebnis eines neuen Erkennungslaufs nach (BE-REC-04).
     *
     * <p>Beide Felder zusammen, nie einzeln: {@code RecurringExpenseService.qualify} setzt den
     * Erstmonat bei einer Lücke <em>und</em> bei einem Preissprung neu (Review PR #298). Eine
     * Zeile mit dem Betrag der jüngsten Reihe und dem Erstmonat der vorherigen behauptete eine
     * Laufzeit, die die Daten nicht hergeben — genau der Fehler, den #298 abgestellt hat.
     *
     * <p>Der Status bleibt unberührt; über ihn entscheidet die Aktivitätsprüfung
     * ({@link #markActive()} / {@link #markEnded()}), nicht das Qualifikationsergebnis.
     *
     * @param amount Betrag des jüngsten qualifizierenden Paars, Skala 2 (ADR-9).
     * @param firstDetectedMonth erster Monat der Reihe in den Daten.
     */
    public void updateFrom(BigDecimal amount, YearMonth firstDetectedMonth) {
        this.amount = amount;
        this.firstDetectedMonth = firstDetectedMonth.toString();
    }

    /**
     * Markiert die Reihe als ausgelaufen (BE-REC-04) — der Empfänger hat in den jüngsten Monaten
     * der Historie nicht mehr abgebucht.
     *
     * <p>Wirkt nur auf {@link RecurringExpenseStatus#DETECTED}. Ein {@code DISMISSED}-Eintrag
     * bleibt unangetastet: «war nie ein Abo» ist die Aussage des Nutzers und darf von einer
     * Beobachtung des Systems nicht überschrieben werden (US-08 AC3). Idempotent auf einem
     * bereits ausgelaufenen Eintrag.
     */
    public void markEnded() {
        if (this.status == RecurringExpenseStatus.DETECTED) {
            this.status = RecurringExpenseStatus.ENDED;
        }
    }

    /**
     * Nimmt eine ausgelaufene Reihe wieder in Betrieb (BE-REC-04) — der Empfänger bucht wieder ab.
     *
     * <p>Gegenstück zu {@link #markEnded()} und mit derselben Schranke: {@code DISMISSED} bleibt
     * {@code DISMISSED}. Ein verneinter Empfänger, der wieder abbucht, wird nicht zum Abo — das
     * ist die Zusage aus US-08 AC3 («künftige Transaktionen desselben Empfängers werden nicht mehr
     * automatisch als wiederkehrend erkannt»). Idempotent auf einem bereits laufenden Eintrag.
     */
    public void markActive() {
        if (this.status == RecurringExpenseStatus.ENDED) {
            this.status = RecurringExpenseStatus.DETECTED;
        }
    }
}
