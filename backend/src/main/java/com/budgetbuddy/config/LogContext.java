package com.budgetbuddy.config;

import java.security.SecureRandom;
import java.util.HexFormat;
import org.slf4j.MDC;

/**
 * Die Schlüssel des Logging-Kontexts (INFRA-37) und die einzige Stelle, die sie schreibt.
 *
 * <p>Der {@link MDC} ist Thread-lokal: Was hier hineingeschrieben wird, hängt automatisch an
 * jeder Log-Zeile desselben Threads, ohne dass die aufrufende Stelle es mitgeben muss. Sichtbar
 * wird es über {@code logging.pattern.level} in {@code application.properties} — ohne die
 * {@code %X{}}-Platzhalter dort bliebe jeder Eintrag hier unsichtbar.
 *
 * <p><strong>Nur diese beiden Schlüssel.</strong> Die User-ID ist eine interne Kennung ohne
 * Namen oder E-Mail, die Request-ID eine Zufallszahl ohne Personenbezug. Weitere
 * Benutzerinformationen gehören nicht in den MDC: Render-Logs liegen ausserhalb der Datenbank
 * und unter anderer Zugriffskontrolle — dieselbe Überlegung wie bei der Redaktion in BE-PDF-06.
 *
 * <p><strong>Wer aufräumt:</strong> {@link LoggingContextFilter} für Request-Threads,
 * {@link MdcTaskDecorator} für die Threads des {@code importExecutor}. Beide rufen
 * {@link #clear()} in einem {@code finally}. Ohne das trüge der nächste Request, der denselben
 * Tomcat-Thread bekommt, die User-ID seines Vorgängers.
 */
public final class LogContext {

    /** ID des authentifizierten Nutzers; fehlt, solange kein gültiges JWT vorliegt. */
    public static final String USER_ID = "userId";

    /** Pro Request vergebene Zufalls-ID; gruppiert die Zeilen eines Requests im Aggregator. */
    public static final String REQUEST_ID = "requestId";

    private static final SecureRandom RANDOM = new SecureRandom();

    private LogContext() {}

    /**
     * Vergibt eine neue Request-ID und legt sie in den MDC.
     *
     * <p>Vier Zufallsbytes ergeben acht Hex-Zeichen — kurz genug, um auf jeder Log-Zeile zu
     * stehen, und weit genug gestreut, damit zwei gleichzeitig laufende Requests im Log
     * unterscheidbar bleiben. Es ist kein Sicherheitsmerkmal: Die ID autorisiert nichts, sie
     * korreliert nur.
     */
    static void newRequestId() {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);
        MDC.put(REQUEST_ID, HexFormat.of().formatHex(bytes));
    }

    /** Legt die ID des authentifizierten Nutzers in den MDC. */
    public static void putUserId(long userId) {
        MDC.put(USER_ID, Long.toString(userId));
    }

    /** Entfernt beide Schlüssel — gehört in ein {@code finally}, nie in den Erfolgspfad allein. */
    public static void clear() {
        MDC.remove(USER_ID);
        MDC.remove(REQUEST_ID);
    }
}
