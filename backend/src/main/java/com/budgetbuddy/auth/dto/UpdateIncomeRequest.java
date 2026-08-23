package com.budgetbuddy.auth.dto;

import java.math.BigDecimal;

/**
 * Request-Body für {@code PUT /api/users/me/income}.
 *
 * <p><strong>Ohne Bean-Validation-Annotationen — bewusst</strong> (BE-AUTH-08). Die Regeln für das
 * Einkommen («&gt; 0», höchstens zwei Nachkommastellen, maximal {@code 99999999.99}) stehen
 * vollständig im {@code UserService} und nur dort. Dieselbe Aufteilung wie bei
 * {@code FixedCostRequest}/{@code FixedCostService}, und aus denselben zwei Gründen: Annotationen
 * greifen erst, wenn ein Controller {@code @Valid} setzt — der Service wäre also ungeschützt,
 * sobald ihn jemand anders aufruft —, und dieselbe Regel an zwei Stellen läuft irgendwann
 * auseinander.
 *
 * <p>Vorher trug dieses Record {@code @NotNull @Positive}. Das deckte die beiden Regeln ab, die es
 * benannte, und liess die dritte offen: {@code 4200.004} kam durch, wurde mit {@code 200 OK}
 * quittiert und von PostgreSQL beim Schreiben in {@code numeric(10,2)} still auf {@code 4200.00}
 * gerundet. Ein {@code @Digits(fraction = 2)} hätte das nicht gelöst — es zählt
 * {@code BigDecimal.scale()} ohne {@code stripTrailingZeros()} und lehnte damit auch
 * {@code 100.000} ab, obwohl das derselbe Wert wie {@code 100.00} ist.
 *
 * @param betrag Monatseinkommen in CHF als {@link BigDecimal} (ADR-9 — kein {@code double}/
 *     {@code float}). Wird vom Service geprüft und auf Rappen normalisiert.
 */
public record UpdateIncomeRequest(BigDecimal betrag) {}
