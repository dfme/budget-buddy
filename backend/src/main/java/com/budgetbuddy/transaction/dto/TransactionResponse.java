package com.budgetbuddy.transaction.dto;

import com.budgetbuddy.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Antwort-DTO einer einzelnen Transaktion, u. a. für {@code PUT /transactions/{id}/category}.
 *
 * <p>{@code betrag} ist die positive Magnitude ({@link BigDecimal}, ADR-9); die Richtung steht in
 * {@code income} ({@code true} = Gutschrift/Einkommen, {@code false} = Belastung/Ausgabe).
 */
public record TransactionResponse(
        Long id,
        LocalDate buchungsdatum,
        String buchungstext,
        BigDecimal betrag,
        boolean income,
        String category) {

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getBuchungsdatum(), tx.getBuchungstext(),
                tx.getBetrag(), tx.isIncome(), tx.getCategory());
    }
}
