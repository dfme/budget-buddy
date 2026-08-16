package com.budgetbuddy.categorization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
 * <p><strong>Der Hash ist gesalzen</strong> (Review PR #174): Ein ungesalzener Hash ist zwar nicht
 * umkehrbar, aber <em>bestätigbar</em> — wer Log-Zugriff hat und «KARDIOLOGIE HIRSLANDEN» vermutet,
 * könnte die Vermutung nachrechnen. Da #157 ausdrücklich Gesundheitsdaten als Beispiel nennt, wäre
 * das die halbe Auskunft. Der Salt wird prozessweit einmalig gezogen: Innerhalb eines Log-Zeitraums
 * bleibt alles korrelierbar, über einen Neustart oder eine zweite Instanz hinweg bewusst nicht —
 * genau diese Grenze nimmt die Bestätigbarkeit. Für den Diagnosezweck («derselbe Text schlug
 * mehrfach fehl», immer innerhalb eines Imports) ist die Prozess-Lebensdauer reichlich.
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

    /**
     * Prozessweit einmalig gezogen und nie geloggt — siehe Klassen-Javadoc. 16 Byte ist die
     * übliche Salt-Länge; kürzer wäre ratebar, länger ohne Zusatznutzen.
     */
    private static final byte[] SALT = newSalt();

    private LogRedaction() {}

    private static byte[] newSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

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

    /**
     * Beschreibt eine Exception <em>ohne</em> ihren Meldungstext: Typ, und falls vorhanden der Typ
     * der Ursache.
     *
     * <p>Die Meldung eines fremden SDK ist ein Fremdstring in der eigenen Log-Zeile — ob eine
     * Anthropic-Fehlermeldung Request-Inhalt zurückspiegeln kann, ist nicht belegt, und der
     * Transaktionstext geht als Prompt hinaus (Review PR #174). Der Typ trägt die eigentliche
     * Diagnose-Achse: {@code RateLimitException} vs. {@code UnauthorizedException} vs. ein
     * {@code AnthropicException ← SocketTimeoutException} beantworten «429 oder Timeout?»
     * vollständig, ohne freien Text zu übernehmen.
     *
     * @param t zu beschreibende Exception; {@code null} wird als {@code <null>} ausgegeben.
     * @return z. B. {@code AnthropicException ← SocketTimeoutException}.
     */
    static String describe(Throwable t) {
        if (t == null) {
            return "<null>";
        }
        Throwable cause = t.getCause();
        if (cause == null || cause == t) {
            return t.getClass().getSimpleName();
        }
        return t.getClass().getSimpleName() + " ← " + cause.getClass().getSimpleName();
    }

    private static String sha256Prefix(String text) {
        try {
            MessageDigest digester = MessageDigest.getInstance("SHA-256");
            digester.update(SALT);
            byte[] digest = digester.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_PREFIX_CHARS);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 ist in jeder JVM garantiert (Java SE Security-Spezifikation).
            throw new IllegalStateException("SHA-256 nicht verfügbar", e);
        }
    }
}
