package com.budgetbuddy.auth.dto;

/**
 * Obergrenzen für String-Felder der Auth-DTOs (BE-AUTH-12, #231) — eine Stelle statt einer Magic
 * Number je DTO. {@code POST /auth/register} ist unauthentifiziert erreichbar ({@code permitAll})
 * und nahm {@code email}/{@code firstName}/{@code lastName} bislang unbegrenzt entgegen.
 *
 * <p><strong>Kein Pendant für {@code password}.</strong> Dessen Obergrenze ist
 * {@link MaxBcryptBytesValidator#MAX_BYTES} (BE-AUTH-10, #200) und zählt UTF-8-Bytes statt
 * Zeichen — eine andere Grösse, die hier nicht dazupasst.
 *
 * <p><strong>Warum keine Flyway-Migration von {@code TEXT} auf {@code VARCHAR(n)}.</strong>
 * Postgres' {@code TEXT} ist storage-identisch mit {@code VARCHAR} ohne Längenangabe — kein
 * Default, keine implizite Grenze (dieselbe Begründung wie in
 * {@code V02__create_transactions_table.sql} und {@code V11__create_recurring_expenses_table.sql}).
 * Diese Konstanten sind deshalb eine reine Anwendungsebene-Regel; die Spalten bleiben {@code TEXT}.
 */
final class AuthFieldLimits {

    /** RFC 5321: maximale Länge einer E-Mail-Adresse. */
    static final int EMAIL_MAX_LENGTH = 254;

    /** Obergrenze für {@code firstName}/{@code lastName} — kein bestehendes Vorbild im Code. */
    static final int NAME_MAX_LENGTH = 50;

    private AuthFieldLimits() {
    }
}
