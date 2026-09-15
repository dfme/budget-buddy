package com.budgetbuddy.recurring.dto;

import com.budgetbuddy.recurring.RecurringExpenseStatus;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ein Eintrag der Abo-Übersicht in der API-Antwort (BE-REC-02).
 *
 * @param id ID des Eintrags.
 * @param payeeKey normalisierter Empfänger, in Grossschreibung.
 * @param amount Betrag der jüngsten erkannten Belastung, Skala 2 (ADR-9).
 * @param status {@code DETECTED} oder {@code DISMISSED}.
 * @param firstDetectedMonth erster Monat der Abo-Reihe in den Daten, als {@code YYYY-MM}-Text.
 * @param createdAt Zeitpunkt der Erkennung.
 * @param isNew {@code true}, solange die zugehörige Benachrichtigung ungelesen ist.
 */
public record RecurringExpenseResponse(
        Long id,
        String payeeKey,
        BigDecimal amount,
        RecurringExpenseStatus status,
        String firstDetectedMonth,
        Instant createdAt,
        boolean isNew) {}
