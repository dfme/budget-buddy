package com.budgetbuddy.transaction;

import com.budgetbuddy.notification.NotificationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Meldet einen fehlgeschlagenen Import an die Glocke (BE-PDF-15, BE-PDF-16).
 *
 * <p><strong>Warum eine eigene Komponente:</strong> Ein Import-Job geht an mehr als einer Stelle
 * auf {@link ImportJobStatus#FAILED} — im {@link ImportJobRunner}, wenn der Lauf selbst abbricht,
 * und im {@link StaleImportJobCleaner}, wenn der Prozess weg ist, der ihn hätte abschliessen
 * sollen. Für den Nutzer ist das derselbe Ausgang; die Ursache sieht er nicht. Typ, Text und
 * Fehlerverhalten liegen deshalb hier an einer Stelle statt in jedem Aufrufer erneut. Lägen sie
 * beim Runner, hinge der Cleaner an einer Klasse, von der er sonst nichts braucht — und der Text
 * wäre trotzdem zweimal geschrieben.
 *
 * <p><strong>Ein Fehlschlag, eine Benachrichtigung.</strong> Die beiden Aufrufer schliessen sich
 * gegenseitig aus: Der Cleaner fasst nur Jobs an, die noch auf {@code RUNNING} stehen, also gerade
 * die, bei denen der Runner nie zum Melden kam.
 *
 * <p><strong>Wer bewusst nicht ruft:</strong> {@code PdfImportService} setzt einen Job auf
 * {@code FAILED}, wenn der Executor den Lauf nicht mehr annimmt
 * ({@code TaskRejectedException}). Das geschieht synchron im Upload-Request — der Nutzer steht in
 * diesem Moment auf der Import-Seite, und ihr Polling zeigt ihm den Fehlschlag ohnehin sofort.
 * Eine Glocken-Meldung wäre dort eine zweite Anzeige desselben Fehlers, nicht die einzige
 * (Entscheid zu BE-PDF-16, #341).
 */
@Component
public class ImportFailureNotifier {

    /**
     * Typ der Fehlschlags-Benachrichtigung (BE-PDF-15) — der freie String, den der
     * {@link NotificationPort} führt.
     */
    public static final String NOTIFICATION_TYPE_FAILED = "IMPORT_FAILED";

    /**
     * Der Anzeigetext. Bewusst ohne Ursache: Ob der Lauf an einer Exception starb oder sein
     * Prozess einem Deploy zum Opfer fiel, ist für den Nutzer dieselbe Lage und dieselbe
     * Handlungsanweisung. Was ihn unterscheidet, gehört ins Log, nicht in die Glocke.
     */
    private static final String MESSAGE =
            "Der Import ist fehlgeschlagen — bitte versuche es erneut.";

    private static final Logger log = LoggerFactory.getLogger(ImportFailureNotifier.class);

    private final NotificationPort notificationPort;

    public ImportFailureNotifier(NotificationPort notificationPort) {
        this.notificationPort = notificationPort;
    }

    /**
     * Legt die Fehlschlags-Benachrichtigung für den Besitzer des Jobs an.
     *
     * <p>Aufzurufen <strong>nachdem</strong> der {@code FAILED}-Status geschrieben ist: Diese
     * Meldung ist die Folge des Zustandswechsels, nicht seine Bedingung.
     *
     * <p>Fängt {@link RuntimeException}, aus demselben Grund wie die Abo-Erkennung im Runner: Ein
     * Fehler hier darf den bereits geschriebenen Status nicht zunichtemachen, indem er aus dem
     * Aufrufer herauswirft. Im {@code Error}-Pfad von {@link ImportJobRunner#run} durchkreuzte er
     * sonst die {@code addSuppressed}-Behandlung des ursprünglichen {@link Error}, im Startlauf des
     * {@link StaleImportJobCleaner} hielte er das Hochfahren auf, und in dessen Schleife über
     * mehrere verwaiste Jobs brächte er die übrigen um ihre Benachrichtigung.
     *
     * @param job der Job, der soeben auf {@code FAILED} gesetzt wurde.
     */
    public void notifyFailed(ImportJob job) {
        try {
            notificationPort.create(
                    job.getUserId(), NOTIFICATION_TYPE_FAILED, job.getId(), MESSAGE);
        } catch (RuntimeException e) {
            log.warn("Import-Job {}: Fehlschlags-Benachrichtigung konnte nicht erzeugt werden.",
                    job.getId(), e);
        }
    }
}
