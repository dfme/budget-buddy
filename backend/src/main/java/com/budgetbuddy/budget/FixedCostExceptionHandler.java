package com.budgetbuddy.budget;

import com.budgetbuddy.budget.dto.FixedCostErrorResponse;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
 * <p>Die Fälle unterscheiden sich bewusst im Body: 400 trägt den Feldnamen, weil US-03 eine
 * feldspezifische Meldung verlangt und sich drei Pflichtfelder einen Status teilen; 404 bleibt
 * body-los, weil dort jede Zusatzauskunft verriete, ob eine fremde ID existiert.
 *
 * <p><strong>Alle 400er dieses Controllers tragen denselben Body.</strong> Das ist der Grund für
 * den {@link HttpMessageNotReadableException}-Handler: fehlender Body, kaputtes JSON und
 * Jackson-Typfehler entstehen, <em>bevor</em> {@link FixedCostController} läuft, und lieferten
 * sonst einen 400 ohne {@code field} — während das generierte OpenAPI-Dokument an {@code POST} und
 * {@code PUT} unbedingt {@code FixedCostErrorResponse} zusagt. Der praktische Fall ist ein
 * Komma-Betrag: {@code "12,50"} kommt in einem Schweizer Wizard als String an, scheitert in
 * Jackson und ist für den Client sonst nicht von einem Serverfehler zu unterscheiden.
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

    /**
     * Fehlender Body, ungültiges JSON oder ein Typfehler in einem Feld — alles, was Jackson beim
     * Lesen des Request-Bodys abbricht, bevor der Controller läuft.
     *
     * <p>Der Feldname kommt aus dem Jackson-Pfad, sofern es einen gibt: bei {@code betrag: "abc"}
     * antwortet der Endpoint mit {@code field: "betrag"} und damit genauso feldspezifisch wie die
     * fachliche Validierung im Service. Nur wenn sich der Fehler keinem Feld zuordnen lässt —
     * kein Body, abgeschnittenes JSON — steht {@code "request"} darin.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public FixedCostErrorResponse handleUnreadableBody(HttpMessageNotReadableException ex) {
        String field = fieldOf(ex);
        return "request".equals(field)
                ? new FixedCostErrorResponse(field,
                        "Der Request-Body fehlt oder ist kein gültiges JSON.")
                : new FixedCostErrorResponse(field, "Wert hat den falschen Typ.");
    }

    @ExceptionHandler(FixedCostNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void handleFixedCostNotFound(FixedCostNotFoundException ex) {
        // Kein Body: 404 ohne Auskunft, ob die ID existiert oder einem anderen User gehört.
    }

    /**
     * Liest den betroffenen Feldnamen aus dem Jackson-Pfad, sonst {@code "request"}.
     *
     * <p>Bewusst nur der <em>Name</em>: {@code InvalidFormatException.getValue()} enthält die
     * Eingabe des Users, und die gehört nicht zurück in die Antwort (Reflected-XSS-Pfad, gleiche
     * Regel wie bei {@link InvalidFixedCostException}).
     */
    private static String fieldOf(HttpMessageNotReadableException ex) {
        if (ex.getCause() instanceof MismatchedInputException mismatch
                && !mismatch.getPath().isEmpty()) {
            String name = mismatch.getPath().getFirst().getFieldName();
            if (name != null) {
                return name;
            }
        }
        return "request";
    }
}
