package com.budgetbuddy.notification.dto;

import java.time.Instant;

/**
 * Eine Benachrichtigung in der API-Antwort (BE-NOTIF-01).
 *
 * @param id ID der Benachrichtigung.
 * @param type Quelle der Benachrichtigung, z. B. {@code "RECURRING_EXPENSE_DETECTED"}.
 * @param referenceId optionaler Verweis auf die auslösende Zeile eines anderen Moduls.
 * @param message Anzeigetext.
 * @param read {@code true}, sobald {@code POST /notifications/{id}/read} aufgerufen wurde.
 * @param createdAt Anlagezeitpunkt.
 */
public record NotificationResponse(
        Long id, String type, Long referenceId, String message, boolean read, Instant createdAt) {}
