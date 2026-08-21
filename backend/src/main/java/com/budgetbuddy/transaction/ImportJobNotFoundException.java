package com.budgetbuddy.transaction;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Kein Import-Job dieser ID für den eingeloggten User (BE-PDF-09).
 *
 * <p>Deckt zwei Fälle mit derselben Antwort ab: Der Job existiert nicht, oder er gehört jemand
 * anderem. Das ist Absicht — Job-IDs sind fortlaufend und damit ratbar, und ein 403 würde
 * verraten, dass unter dieser ID gerade ein fremder Import läuft.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ImportJobNotFoundException extends RuntimeException {

    public ImportJobNotFoundException() {
        super("Kein Import-Job dieser ID für den eingeloggten User");
    }
}
