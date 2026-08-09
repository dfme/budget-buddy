package com.budgetbuddy.auth;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Lese-Port auf das monatliche Einkommen eines Users — die einzige Information aus dem
 * {@code auth}-Modul, die das {@code budget}-Modul für die Fixkosten-Warnung aus US-03 braucht
 * («Fixkosten ≥ Einkommen», BE-FC-02).
 *
 * <p>Über dieses Interface liest der {@code FixedCostService}, ohne direkt auf
 * {@link UserRepository} oder {@link UserService} zuzugreifen (Modulgrenze, siehe CLAUDE.md).
 * Gleiche Bauart wie {@code CategorizationPort}/{@code CategoryLearningPort}: das Interface steht
 * im <em>liefernden</em> Modul, nicht im aufrufenden.
 *
 * <p>Bewusst schmal: der Port gibt einen Betrag heraus, kein {@code User} und kein
 * {@code UserProfileResponse}. E-Mail und Passwort-Hash haben im budget-Modul nichts verloren, und
 * ein breiterer Rückgabetyp würde sie dorthin mitnehmen.
 */
public interface UserIncomePort {

    /**
     * Liefert das monatliche Einkommen des Users in CHF.
     *
     * @param userId ID des eingeloggten Users (aus dem JWT).
     * @return leer, wenn der User kein Einkommen erfasst hat ({@code monthly_income IS NULL},
     *     Onboarding noch nicht abgeschlossen) <em>oder</em> kein User mit dieser ID existiert.
     *     Für den Aufrufer sind beide Fälle gleichbedeutend: es gibt keinen Vergleichswert.
     */
    Optional<BigDecimal> findMonthlyIncome(long userId);
}
