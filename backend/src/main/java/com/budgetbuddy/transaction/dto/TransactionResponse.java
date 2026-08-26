package com.budgetbuddy.transaction.dto;

import com.budgetbuddy.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Antwort-DTO einer einzelnen Transaktion, u. a. für {@code PUT /transactions/{id}/category}.
 *
 * <p>{@code betrag} ist die positive Magnitude ({@link BigDecimal}, ADR-9); die Richtung steht in
 * {@code income} ({@code true} = Gutschrift/Einkommen, {@code false} = Belastung/Ausgabe).
 *
 * <p>{@code buchungsdetails} trägt Gegenpartei und Verwendungszweck (BE-PDF-07). Der Wert ist
 * {@code null}, wenn die Buchung keine Detailzeilen hatte oder vor BE-PDF-07 importiert wurde —
 * anders als bei {@code category} löst der Lesepfad das <em>nicht</em> auf einen Ersatzwert auf:
 * Es gibt keinen, der nicht etwas Falsches behaupten würde.
 */
public record TransactionResponse(
        Long id,
        LocalDate buchungsdatum,
        String buchungstext,
        String buchungsdetails,
        BigDecimal betrag,
        boolean income,
        String category) {

    public static TransactionResponse from(Transaction tx) {
        return new TransactionResponse(tx.getId(), tx.getBuchungsdatum(), tx.getBuchungstext(),
                tx.getBuchungsdetails(), tx.getBetrag(), tx.isIncome(), tx.getCategory());
    }
}
