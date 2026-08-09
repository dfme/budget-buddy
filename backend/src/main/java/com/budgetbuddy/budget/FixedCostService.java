package com.budgetbuddy.budget;

import com.budgetbuddy.auth.UserIncomePort;
import com.budgetbuddy.budget.dto.FixedCostRequest;
import com.budgetbuddy.budget.dto.FixedCostResponse;
import com.budgetbuddy.budget.dto.FixedCostSummaryResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD, Monats-Normalisierung und Einkommens-Warnung für Fixkosten (BE-FC-02, US-03).
 *
 * <p><strong>Normalisierung:</strong> jede Position wird einzeln auf einen Monatsbetrag umgerechnet
 * — {@code monatlich} ÷ 1, {@code quartalsweise} ÷ 3, {@code jaehrlich} ÷ 12 — und dabei auf
 * Rappen gerundet ({@link RoundingMode#HALF_UP}). Die Monatssumme ist die Summe dieser bereits
 * gerundeten Zeilen. Erst die Summe zu runden wäre mathematisch exakter, aber dann addieren sich
 * die im Wizard sichtbaren Zeilenbeträge nicht mehr zur angezeigten Summe (bis 1 Rappen Abweichung
 * je Position). US-03 verlangt Rappen-Genauigkeit im Safe-to-Spend; eine Summe, die der User nicht
 * nachrechnen kann, ist hier der grössere Schaden.
 *
 * <p>Sämtliche Beträge sind {@link BigDecimal} (ADR-9) — nie {@code double}/{@code float}.
 *
 * <p><strong>Mandantentrennung:</strong> jeder Zugriff läuft über die user-gebundenen Methoden des
 * {@link FixedCostRepository}. Die geerbten {@code findById}/{@code deleteById} werden bewusst
 * nirgends verwendet: sie wären auf dieser Entity ein IDOR.
 *
 * <p><strong>Validierung:</strong> die Regeln aus US-03 stehen hier und nur hier — {@code FixedCost}
 * delegiert sie ausdrücklich an diesen Service, und {@link FixedCostRequest} trägt deshalb keine
 * Bean-Validation-Annotationen.
 *
 * <p>Das Einkommen für die Warnung kommt über den {@link UserIncomePort} aus dem {@code auth}-Modul
 * — kein direkter Zugriff auf dessen Repository (Modulgrenze, CLAUDE.md).
 */
@Service
public class FixedCostService {

    /** Rappen — Zielskala aller Beträge nach aussen. */
    private static final int RAPPEN_SCALE = 2;

    private static final int MAX_BEZEICHNUNG_LENGTH = 100;

    /** Kapazität der Spalte {@code fixed_costs.betrag DECIMAL(10,2)} aus Migration V03. */
    private static final BigDecimal MAX_BETRAG = new BigDecimal("99999999.99");

    private static final BigDecimal MONATE_PRO_QUARTAL = new BigDecimal("3");
    private static final BigDecimal MONATE_PRO_JAHR = new BigDecimal("12");

    private final FixedCostRepository fixedCostRepository;
    private final UserIncomePort userIncomePort;

    public FixedCostService(FixedCostRepository fixedCostRepository, UserIncomePort userIncomePort) {
        this.fixedCostRepository = fixedCostRepository;
        this.userIncomePort = userIncomePort;
    }

    /**
     * Liefert alle Fixkosten des Users mit Monatssumme und Einkommens-Warnung.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return Übersicht; bei einem User ohne Positionen leere Liste und Summe {@code 0.00}.
     */
    @Transactional(readOnly = true)
    public FixedCostSummaryResponse list(long userId) {
        List<FixedCost> entries = fixedCostRepository.findByUserIdOrderByIdAsc(userId);

        List<FixedCostResponse> items = new ArrayList<>(entries.size());
        BigDecimal summeMonatlich = BigDecimal.ZERO.setScale(RAPPEN_SCALE);
        for (FixedCost entry : entries) {
            FixedCostResponse item = toResponse(entry);
            items.add(item);
            summeMonatlich = summeMonatlich.add(item.monatsbetrag());
        }

        // Verglichen wird gegen das ungerundete Einkommen; gerundet wird erst für die Antwort.
        // users.monthly_income ist nur auf > 0 geprüft (UpdateIncomeRequest), nicht auf Rappen —
        // eine Rundung vor dem Vergleich könnte das Flag kippen.
        BigDecimal monthlyIncome = userIncomePort.findMonthlyIncome(userId).orElse(null);
        boolean exceedsIncome = monthlyIncome != null && summeMonatlich.compareTo(monthlyIncome) >= 0;

        return new FixedCostSummaryResponse(
                List.copyOf(items),
                summeMonatlich,
                monthlyIncome == null
                        ? null
                        : monthlyIncome.setScale(RAPPEN_SCALE, RoundingMode.HALF_UP),
                exceedsIncome);
    }

    /**
     * Liefert eine einzelne Position des Users.
     *
     * @throws FixedCostNotFoundException wenn die ID nicht existiert oder einem anderen User gehört.
     */
    @Transactional(readOnly = true)
    public FixedCostResponse get(long userId, long fixedCostId) {
        return toResponse(findOwned(userId, fixedCostId));
    }

    /**
     * Legt eine neue Fixkosten-Position für den User an.
     *
     * @throws InvalidFixedCostException wenn ein Feld die Regeln aus US-03 verletzt.
     */
    @Transactional
    public FixedCostResponse create(long userId, FixedCostRequest request) {
        ValidInput input = validate(request);
        FixedCost saved = fixedCostRepository.save(
                new FixedCost(userId, input.bezeichnung(), input.betrag(), input.intervall()));
        return toResponse(saved);
    }

    /**
     * Ändert eine bestehende Position des Users. Der Besitzer wechselt dabei nie — {@code userId}
     * ist am Entity ohne Setter.
     *
     * @throws FixedCostNotFoundException wenn die ID nicht existiert oder einem anderen User gehört.
     * @throws InvalidFixedCostException wenn ein Feld die Regeln aus US-03 verletzt.
     */
    @Transactional
    public FixedCostResponse update(long userId, long fixedCostId, FixedCostRequest request) {
        ValidInput input = validate(request);

        FixedCost entry = findOwned(userId, fixedCostId);
        entry.setBezeichnung(input.bezeichnung());
        entry.setBetrag(input.betrag());
        entry.setIntervall(input.intervall());

        return toResponse(fixedCostRepository.save(entry));
    }

    /**
     * Löscht eine Position des Users.
     *
     * @throws FixedCostNotFoundException wenn die ID nicht existiert oder einem anderen User gehört
     *     — in beiden Fällen löscht das Repository 0 Zeilen.
     */
    @Transactional
    public void delete(long userId, long fixedCostId) {
        if (fixedCostRepository.deleteByIdAndUserId(fixedCostId, userId) == 0) {
            throw new FixedCostNotFoundException(userId, fixedCostId);
        }
    }

    private FixedCost findOwned(long userId, long fixedCostId) {
        return fixedCostRepository
                .findByIdAndUserId(fixedCostId, userId)
                .orElseThrow(() -> new FixedCostNotFoundException(userId, fixedCostId));
    }

    /**
     * Mappt eine Entity auf die Antwort und setzt beide Beträge auf Skala 2.
     *
     * <p>{@link RoundingMode#UNNECESSARY} auf {@code betrag} ist Absicht: aus SQLite kommt der Wert
     * mit Skala 0 oder 1 zurück (#141), und Aufrunden ist dort nie nötig. Läge doch je ein Wert mit
     * mehr als zwei Nachkommastellen in der Spalte {@code DECIMAL(10,2)}, wäre das ein
     * Datendefekt — der soll laut scheitern und nicht still gerundet werden.
     */
    private static FixedCostResponse toResponse(FixedCost entry) {
        return new FixedCostResponse(
                entry.getId(),
                entry.getBezeichnung(),
                entry.getBetrag().setScale(RAPPEN_SCALE, RoundingMode.UNNECESSARY),
                entry.getIntervall().getLabel(),
                monatsbetrag(entry.getBetrag(), entry.getIntervall()));
    }

    /** Rechnet einen Intervall-Betrag auf einen rappen-gerundeten Monatsbetrag um. */
    private static BigDecimal monatsbetrag(BigDecimal betrag, Intervall intervall) {
        return switch (intervall) {
            case MONATLICH -> betrag.setScale(RAPPEN_SCALE, RoundingMode.UNNECESSARY);
            case QUARTALSWEISE ->
                    betrag.divide(MONATE_PRO_QUARTAL, RAPPEN_SCALE, RoundingMode.HALF_UP);
            case JAEHRLICH -> betrag.divide(MONATE_PRO_JAHR, RAPPEN_SCALE, RoundingMode.HALF_UP);
        };
    }

    /**
     * Prüft die Pflichtfelder aus US-03 und liefert die bereinigten Werte.
     *
     * <p>Die Meldungen benennen die verletzte Regel und wiederholen die Eingabe nicht — sie gehen
     * in eine HTTP-Antwort.
     */
    private static ValidInput validate(FixedCostRequest request) {
        if (request == null) {
            throw new InvalidFixedCostException("request", "Es wurden keine Daten übermittelt.");
        }
        return new ValidInput(
                validateBezeichnung(request.bezeichnung()),
                validateBetrag(request.betrag()),
                validateIntervall(request.intervall()));
    }

    private static String validateBezeichnung(String bezeichnung) {
        if (bezeichnung == null || bezeichnung.isBlank()) {
            throw new InvalidFixedCostException("bezeichnung", "Bezeichnung darf nicht leer sein.");
        }
        String trimmed = bezeichnung.trim();
        if (trimmed.length() > MAX_BEZEICHNUNG_LENGTH) {
            throw new InvalidFixedCostException(
                    "bezeichnung",
                    "Bezeichnung darf höchstens " + MAX_BEZEICHNUNG_LENGTH + " Zeichen lang sein.");
        }
        return trimmed;
    }

    private static BigDecimal validateBetrag(BigDecimal betrag) {
        if (betrag == null) {
            throw new InvalidFixedCostException("betrag", "Betrag ist erforderlich.");
        }
        if (betrag.signum() <= 0) {
            throw new InvalidFixedCostException("betrag", "Betrag muss grösser als 0 sein.");
        }
        // stripTrailingZeros(), damit "100.00" (Skala 2) und "100.000" (Skala 3) gleich behandelt
        // werden: entscheidend ist der Wert, nicht wie viele Nullen der Client angehängt hat.
        if (betrag.stripTrailingZeros().scale() > RAPPEN_SCALE) {
            throw new InvalidFixedCostException(
                    "betrag", "Betrag darf höchstens zwei Nachkommastellen haben.");
        }
        if (betrag.compareTo(MAX_BETRAG) > 0) {
            throw new InvalidFixedCostException(
                    "betrag", "Betrag darf 99'999'999.99 nicht überschreiten.");
        }
        return betrag.setScale(RAPPEN_SCALE, RoundingMode.UNNECESSARY);
    }

    private static Intervall validateIntervall(String intervall) {
        if (intervall == null || intervall.isBlank()) {
            throw new InvalidFixedCostException("intervall", "Intervall ist erforderlich.");
        }
        try {
            return Intervall.fromLabel(intervall);
        } catch (IllegalArgumentException e) {
            throw new InvalidFixedCostException(
                    "intervall",
                    "Intervall muss monatlich, quartalsweise oder jaehrlich sein.");
        }
    }

    /** Geprüfte und bereinigte Eingabe — getrimmte Bezeichnung, Betrag mit Skala 2, Enum. */
    private record ValidInput(String bezeichnung, BigDecimal betrag, Intervall intervall) {}
}
