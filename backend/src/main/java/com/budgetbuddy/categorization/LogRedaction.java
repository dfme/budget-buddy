package com.budgetbuddy.categorization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Redigiert Transaktionstexte und Händler-Patterns für Log-Ausgaben (BE-PDF-06, Risiko #2, nDSG).
 *
 * <p>Kontoauszug-Texte sind Zahlungsdaten und dürfen nicht im Klartext in Logs stehen — Render-Logs
 * unterliegen einer anderen Zugriffskontrolle als die Datenbank. Statt des Volltexts wird
 * {@code <len=NN sha256=xxxxxxxx>} geloggt: Die Länge plus ein SHA-256-Präfix macht identische
 * Texte über Log-Zeilen hinweg korrelierbar (z. B. «dieselbe Transaktion schlug dreimal fehl»),
 * ohne dass sich aus dem Log eine Zahlung rekonstruieren lässt.
 *
 * <p>Bewusst gibt es keinen {@code TRACE}-Pfad mit Volltext: Ein spätere Hochdrehen des Log-Levels
 * zur Fehlersuche darf keine Zahlungsdaten freilegen.
 */
final class LogRedaction {

    /**
     * 8 Hex-Zeichen (32 Bit) genügen zur Korrelation innerhalb eines Log-Zeitraums; ein längeres
     * Präfix würde nur die Log-Zeilen verbreitern, ohne Diagnose-Nutzen.
     */
    private static final int HASH_PREFIX_CHARS = 8;

    private LogRedaction() {}

    /**
     * @param text zu redigierender Text; {@code null} wird als {@code <null>} ausgegeben.
     * @return korrelierbare, nicht rekonstruierbare Darstellung, z. B. {@code <len=34 sha256=ab12cd34>}.
     */
    static String redact(String text) {
        if (text == null) {
            return "<null>";
        }
        return "<len=%d sha256=%s>".formatted(text.length(), sha256Prefix(text));
    }

    private static String sha256Prefix(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_PREFIX_CHARS);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 ist in jeder JVM garantiert (Java SE Security-Spezifikation).
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
