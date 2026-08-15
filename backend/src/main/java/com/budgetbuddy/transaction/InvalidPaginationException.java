package com.budgetbuddy.transaction;

/**
 * Die Seitenparameter von {@code GET /transactions} liegen ausserhalb des erlaubten Bereichs
 * (FE-CAT-05): {@code page} ist negativ oder {@code size} liegt nicht zwischen 1 und
 * {@link TransactionListService#MAX_PAGE_SIZE}. Wird vom {@link TransactionExceptionHandler} auf
 * HTTP 400 abgebildet.
 *
 * <p>Die Obergrenze ist der Punkt, an dem die Pagination überhaupt etwas wert ist: ohne sie liesse
 * sich der Vollload, den US-13 ausschliesst, mit einem grossen {@code size} wiederherstellen.
 */
public class InvalidPaginationException extends RuntimeException {

    public InvalidPaginationException(String message) {
        super(message);
    }
}
