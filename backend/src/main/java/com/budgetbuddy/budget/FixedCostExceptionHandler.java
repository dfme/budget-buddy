package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.FixedCostErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Bildet die Exceptions des {@link FixedCostController} auf HTTP-Status ab (BE-FC-03).
 *
 * <p>Auf den Controller beschränkt statt global: {@link InvalidFixedCostException} und
 * {@link FixedCostNotFoundException} sind budget-eigene Typen, und ein globales Advice würde die
 * Zuständigkeit über Modulgrenzen ziehen — dieselbe Aufteilung wie beim
 * {@code TransactionExceptionHandler} und beim {@code PdfImportExceptionHandler}.
 *
 * <p>Die beiden Fälle unterscheiden sich bewusst im Body: 400 trägt den Feldnamen, weil US-03 eine
 * feldspezifische Meldung verlangt und sich drei Pflichtfelder einen Status teilen; 404 bleibt
 * body-los, weil dort jede Zusatzauskunft verriete, ob eine fremde ID existiert.
 */
@RestControllerAdvice(assignableTypes = FixedCostController.class)
public class FixedCostExceptionHandler {

    @ExceptionHandler(InvalidFixedCostException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public FixedCostErrorResponse handleInvalidFixedCost(InvalidFixedCostException ex) {
        // getMessage() stammt aus dem Service und nennt nur die verletzte Regel — die Eingabe des
        // Users wird dort bewusst nicht wiederholt (siehe InvalidFixedCostException).
        return new FixedCostErrorResponse(ex.getField(), ex.getMessage());
    }

    @ExceptionHandler(FixedCostNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleFixedCostNotFound(FixedCostNotFoundException ex) {
        // Kein Body: 404 ohne Auskunft, ob die ID existiert oder einem anderen User gehört.
    }
}
