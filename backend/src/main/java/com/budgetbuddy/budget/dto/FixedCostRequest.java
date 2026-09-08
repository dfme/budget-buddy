package com.budgetbuddy.budget.dto;

import java.math.BigDecimal;

/**
 * Eingabe zum Anlegen und Ändern einer Fixkosten-Position (BE-FC-02, US-03).
 *
 * <p><strong>Ohne Bean-Validation-Annotationen — bewusst.</strong> Die Regeln aus US-03
 * («Bezeichnung nicht leer», «Betrag > 0», «Intervall ∈ {monatlich, quartalsweise, jährlich}»)
 * werden vollständig im {@code FixedCostService} durchgesetzt, nicht beim Deserialisieren. Das hat
 * zwei Gründe: {@code FixedCost} delegiert die fachliche Validierung ausdrücklich an den Service,
 * und Annotationen greifen erst, wenn ein Controller {@code @Valid} setzt — der Service ist damit
 * ungeschützt, sobald er von anderswo aufgerufen wird. Dieselbe Regel an zwei Stellen würde
 * ausserdem irgendwann auseinanderlaufen.
 *
 * <p>Die CHF-Betragsregel selbst liegt seit BE-FC-04 in {@code money.ChfAmounts} — sie stand
 * zuvor gleichlautend auch im {@code auth}-Modul. Der Service ruft sie auf und behält seine
 * eigene {@code InvalidFixedCostException} mit ihrem feldspezifischen Text; geteilt wird die
 * Prüfung, nicht die Meldung (ADR-9-Nachtrag).
 *
 * <p>{@code UpdateIncomeRequest} folgt seit BE-AUTH-08 derselben Aufteilung: Es trug vorher
 * {@code @NotNull}/{@code @Positive}, was für {@code users.monthly_income} zu wenig war — die
 * Rappen- und Kapazitätsregel liegt jetzt ebenfalls im Service. Die beiden DTOs weichen also
 * nicht mehr voneinander ab.
 *
 * @param bezeichnung Anzeigename der Position, z. B. {@code "Miete"}. Wird vom Service getrimmt.
 * @param betrag Betrag in CHF pro {@code intervall} ({@link BigDecimal}, ADR-9).
 * @param intervall Intervall-Label exakt wie im API-Contract: {@code "monatlich"},
 *     {@code "quartalsweise"} oder {@code "jaehrlich"} (ASCII, siehe
 *     {@code com.budgetbuddy.budget.Intervall}).
 */
public record FixedCostRequest(String bezeichnung, BigDecimal betrag, String intervall) {}
