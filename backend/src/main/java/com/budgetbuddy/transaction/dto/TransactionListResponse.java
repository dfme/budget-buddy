package com.budgetbuddy.transaction.dto;

import java.util.List;

/**
 * Antwort für {@code GET /transactions?month=YYYY-MM} (FE-CAT-05, US-13).
 *
 * <p>Eine <em>Seite</em> der Ausgabenliste, nicht die ganze Liste: US-13 schliesst den
 * ungepaginierten Vollload aus. Bewusst ein Objekt statt eines nackten Arrays — ohne
 * {@link #hasMore()} könnte das Frontend den «Weitere laden»-Button nicht ausblenden, und im
 * Antwort-Schema ist das Signal auffindbar, während es als HTTP-Header an Swagger UI vorbeiliefe.
 *
 * <p>Ohne Gesamtzahl: die Anzeige braucht sie nicht, und sie zu liefern hiesse, neben jeder Seite
 * eine zweite Query über den ganzen Monat laufen zu lassen.
 *
 * @param transactions die Buchungen dieser Seite, absteigend nach Buchungsdatum und bei Gleichstand
 *     nach ID. Leer, wenn die Seite hinter dem Ende liegt oder der Monat keine Ausgaben enthält.
 * @param hasMore {@code true}, wenn hinter dieser Seite weitere Buchungen folgen.
 */
public record TransactionListResponse(List<TransactionResponse> transactions, boolean hasMore) {}
