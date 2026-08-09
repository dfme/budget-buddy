package com.budgetbuddy.budget.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Antwort für die Fixkosten-Übersicht eines Users (BE-FC-02, US-03) — die Positionen plus die
 * beiden daraus abgeleiteten Werte, die der Onboarding-Wizard und das Dashboard brauchen.
 *
 * @param fixedCosts alle Positionen des Users, stabil nach Anlage-Reihenfolge sortiert. Leer, wenn
 *     noch nichts erfasst wurde.
 * @param summeMonatlich Summe der {@link FixedCostResponse#monatsbetrag()} aller Positionen, Skala
 *     2. Bewusst die Summe der <em>bereits gerundeten</em> Zeilen: so ergibt die angezeigte Liste
 *     exakt die angezeigte Summe. Bei leerer Liste {@code 0.00}.
 * @param monthlyIncome monatliches Einkommen des Users in CHF, Skala 2 — oder {@code null}, wenn
 *     noch keines erfasst ist (Onboarding läuft). {@code null} ist damit für den Client von
 *     «Einkommen reicht» unterscheidbar.
 * @param exceedsIncome {@code true}, wenn {@code summeMonatlich ≥ monthlyIncome} — dann kann kein
 *     Safe-to-Spend berechnet werden und US-03 verlangt die Warnung «Deine Fixkosten übersteigen
 *     dein Einkommen». Ohne erfasstes Einkommen immer {@code false}: ohne Vergleichswert gibt es
 *     keine belegbare Aussage, und im Wizard ist das der Normalzustand.
 */
public record FixedCostSummaryResponse(
        List<FixedCostResponse> fixedCosts,
        BigDecimal summeMonatlich,
        BigDecimal monthlyIncome,
        boolean exceedsIncome) {}
