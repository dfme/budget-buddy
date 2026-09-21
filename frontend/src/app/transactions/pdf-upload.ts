import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { NotificationService } from '../notifications/notification.service';

import { Amount } from '../shared/amount/amount';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { CATEGORIES } from '../shared/category';
import { Input } from '../shared/input/input';
import { Meter } from '../shared/meter/meter';
import { Modal } from '../shared/modal/modal';
import { Notice } from '../shared/notice/notice';
import { ImportErrorResponse } from './import-error.model';
import { ImportJobStatusResponse } from './import-response.model';
import { ImportPollTimeoutError, PdfImportService } from './pdf-import.service';
import { Transaction } from './transaction.model';
import { TransactionService } from './transaction.service';

/** Serverseitiges Upload-Limit aus BE-PDF-03 — client-seitig vorab geprüft (US-04). */
const MAX_PDF_BYTES = 10 * 1024 * 1024;

/**
 * Meldung zum 409-Duplikat. Bewusst ohne Retry-Hinweis: ein zweiter Versuch scheitert identisch,
 * weiter kommt der User nur über «Trotzdem importieren» im Dialog.
 */
const DUPLICATE_MESSAGE = 'Dieser Kontoauszug wurde bereits importiert.';

/**
 * Meldung für einen Import, der serverseitig ins Zeitbudget lief. Er ist trotzdem vollständig
 * gespeichert — nur ein Teil hat keine automatische Kategorie bekommen (BE-PDF-09).
 */
const DEGRADED_HINT =
  ' Ein Teil davon konnte nicht automatisch kategorisiert werden und steht unter «Sonstiges» —' +
  ' du kannst die Kategorien von Hand korrigieren.';

/** Meldung, wenn der Hintergrundlauf selbst gescheitert ist. */
const JOB_FAILED_MESSAGE = 'Der Import ist fehlgeschlagen — bitte versuche es erneut.';

/**
 * Meldung, wenn der Status-Endpoint den Job nicht kennt (FE-NOTIF-05).
 *
 * <p>Erreichbar über `?job=` aus einer alten Benachrichtigung oder einer editierten Adresse. Das
 * Backend antwortet für einen unbekannten wie für einen fremden Job gleich mit 404
 * (`PdfImportController`, `findByIdAndUserId`) — die Meldung unterscheidet das deshalb auch
 * nicht. Bewusst ohne Retry-Hinweis: es gibt nichts zu wiederholen, nur etwas Neues hochzuladen.
 */
const JOB_NOT_FOUND_MESSAGE = 'Dieser Import ist nicht mehr abrufbar.';

/**
 * Meldung, wenn ein abgeschlossener Import mit `total > 0` eine leere Liste liefert
 * (FE-NOTIF-05).
 *
 * <p>Ein Defensiv-Zweig für die Invariante «{@link PdfUpload.importedTransactions} ist nie eine
 * leere Liste», kein bekannter Anwendungsfall: `GET /api/import/{jobId}/transactions` löst die
 * Buchungen über `userId + pdfSha256` auf, nicht über die Job-ID
 * (`PdfImportService.listTransactions`). Nach «Trotzdem importieren» (FE-PDF-03) liefert die
 * Benachrichtigung des *alten* Jobs deshalb die Zeilen des neuesten Imports — nicht nichts. Der
 * einzige Weg, auf dem Buchungen wirklich verschwinden, ist die Kontolöschung, und die nimmt den
 * Job mit. Der Satz behauptet darum keine Ursache.
 */
const LIST_EMPTY_MESSAGE = 'Zu diesem Import sind keine Buchungen mehr vorhanden.';

/**
 * Meldung, wenn die Liste der importierten Buchungen nicht geladen werden konnte (FE-PDF-04).
 *
 * <p>Bewusst kein Fehlschlag des Imports: Die Buchungen sind gespeichert, nur ihre Anzeige fehlt.
 * Die Erfolgsmeldung darüber bleibt deshalb stehen, und der Satz nennt mit der
 * Kategorie-Übersicht den Weg, der trotzdem zum Ziel führt.
 */
const LIST_FAILED_MESSAGE =
  'Die importierten Buchungen konnten nicht geladen werden. Sie sind gespeichert — du findest ' +
  'sie in der Kategorie-Übersicht.';

/** Meldung, wenn eine Kategorie-Korrektur nicht gespeichert werden konnte (FE-PDF-04). */
const CATEGORY_SAVE_FAILED_MESSAGE = 'Die Kategorie konnte nicht gespeichert werden.';

/**
 * Die Statusabfrage hat aufgegeben, ohne einen Endzustand gesehen zu haben.
 *
 * <p>Bewusst weder Erfolg noch Fehlschlag: Der Import kann durchgelaufen sein, während nur die
 * Anzeige den Anschluss verloren hat. «Erneut versuchen» wäre hier der falsche Rat — er
 * erzeugte womöglich eine Dublette. Der Reload zeigt den tatsächlichen Stand.
 */
const POLL_TIMEOUT_MESSAGE =
  'Der Import läuft ungewöhnlich lange — der Status ist unbekannt. Bitte lade die Seite neu, ' +
  'um zu sehen, ob er durchgelaufen ist.';

/** Ausgang des letzten Uploads — Erfolg mit Anzahl oder Fehler mit fertiger Nutzermeldung. */
export type ImportOutcome =
  | { kind: 'success'; count: number; degraded: boolean }
  | { kind: 'error'; message: string };

/** Stand des laufenden Imports, wie ihn der Fortschrittsbalken anzeigt. */
export interface ImportProgress {
  processed: number;
  total: number;
}

/**
 * PDF-Upload für den Kontoauszug-Import (FE-PDF-01/FE-PDF-02/FE-PDF-03, US-04).
 *
 * <p>Dropzone mit Drag-and-Drop plus File-Picker als tastaturbedienbare
 * Alternative. Vor dem Upload wird client-seitig validiert (nur `.pdf`,
 * max. 10 MB); während der Import läuft, zeigt die Dropzone den Fortschritt
 * und nimmt keine weiteren Dateien an.
 *
 * <p><strong>Zweistufig seit BE-PDF-09 / ADR-14:</strong> `POST /api/import/pdf`
 * parst das PDF und kehrt mit einer Job-ID zurück; die Kategorisierung läuft
 * serverseitig weiter. Die Komponente pollt danach
 * `GET /api/import/{jobId}/status` und zeigt `processed`/`total` als Balken.
 * Vorher blockierte der Upload bis zu 30 Sekunden ohne jede Rückmeldung und
 * verwarf danach den ganzen Import (#192).
 *
 * <p>Der Ausgang landet differenziert in {@link importOutcome}: Erfolg trägt
 * die Anzahl importierter Transaktionen, Fehler eine bereits formulierte
 * Meldung ({@link PdfUpload.importErrorMessage} mappt Status + `reason` des
 * Backends).
 *
 * <p>Das 409-Duplikat ist der einzige Fehler, der nicht als Meldung endet: er
 * öffnet den Bestätigungsdialog ({@link duplicateFile}). «Trotzdem importieren»
 * wiederholt den Upload mit `force=true` und ersetzt damit den früheren Import,
 * «Abbrechen» schliesst den Dialog und lässt die Daten unverändert.
 *
 * <p><strong>Seit FE-PDF-04 endet der Import nicht bei der Zahl:</strong> Nach einem
 * erfolgreichen Lauf mit mindestens einer Buchung lädt die Komponente über
 * `GET /api/import/{jobId}/transactions` (BE-PDF-14) die Buchungen selbst nach und zeigt sie
 * unter der Erfolgsmeldung — jede mit einem Dropdown, über das sich ihre Kategorie an Ort und
 * Stelle korrigieren lässt ({@link changeCategory}). Vorher musste der Nutzer dafür auf die
 * Kategorie-Übersicht wechseln und dort den Monat des Auszugs suchen.
 *
 * <p><strong>Glocke nach dem Import (FE-NOTIF-04, #336):</strong> Die Abo-Erkennung läuft im
 * Backend synchron am Ende des Jobs, ihre Benachrichtigung existiert also, sobald der Poll
 * `DONE` meldet. Die Glocke lädt aber nur bei Login und Navigation (FE-NOTIF-01, kein Polling)
 * — wer auf dem Import-Screen bleibt, sähe das Badge erst nach dem nächsten Seitenwechsel.
 * Deshalb stösst der `DONE`-Zweig einmal {@link NotificationService.reload} an: kein Polling,
 * ein gezielter Reload an der einzigen Stelle, an der das Frontend weiss, dass gerade etwas
 * entstanden sein kann — und bewusst `reload` statt `load`, damit er sich nicht an ein `GET`
 * hängt, das vor dem Abschluss losging und den Stand davor liefern würde.
 *
 * <p><strong>Einstieg von aussen über `?job=` (FE-NOTIF-05, #348):</strong> Die Glocke führt bei
 * einer Import-Benachrichtigung (BE-PDF-15) nach `/import?job=<jobId>`. Die Komponente nimmt
 * einen so übergebenen Job über denselben {@link trackJob}-Pfad auf wie nach einem Upload: Der
 * erste Poll liefert den Endzustand, `DONE` lädt die Buchungen nach, `FAILED` zeigt die
 * Fehlermeldung, ein 404 die Meldung «nicht mehr abrufbar». Umgekehrt schreibt jeder Upload
 * seine Job-ID in die URL, damit ein Reload dieselbe Übersicht zeigt. Muster wie `?month=` in
 * `CategoryOverview`: `queryParamMap` → {@link syncFromUrl}, mit einer Wache gegen das Echo der
 * eigenen Navigation. Ob ein Job dem Nutzer gehört, entscheidet allein das Backend (404 für
 * fremde Jobs) — hier wird nichts gefiltert.
 */
@Component({
  selector: 'app-pdf-upload',
  imports: [Amount, Button, Card, DatePipe, Input, Meter, Modal, Notice],
  templateUrl: './pdf-upload.html',
  styleUrl: './pdf-upload.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfUpload {
  private readonly importService = inject(PdfImportService);
  private readonly transactionService = inject(TransactionService);
  private readonly notificationService = inject(NotificationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Die 17 Kategorien des Dropdowns — dieselbe Quelle wie die Kategorie-Übersicht (FE-CAT-03). */
  protected readonly categories = CATEGORIES;

  /** `true`, solange Upload oder Kategorisierung laufen — sperrt die Dropzone. */
  readonly uploading = signal(false);

  /**
   * Stand des laufenden Imports oder `null`, solange das PDF noch geparst wird.
   *
   * <p>Der Nenner steht ab der Upload-Antwort fest; bis dahin gibt es keine Zahl anzuzeigen und
   * die Dropzone zeigt nur den Spinner.
   */
  readonly progress = signal<ImportProgress | null>(null);

  /** Fortschritt in Prozent für den Balken; 0, solange kein Nenner bekannt ist. */
  readonly progressPercent = computed(() => {
    const current = this.progress();
    if (!current || current.total === 0) {
      return 0;
    }
    return Math.round((current.processed / current.total) * 100);
  });

  /** Client-seitige Validierungsmeldung oder `null`. */
  readonly errorMessage = signal<string | null>(null);

  /** Ausgang des letzten Uploads oder `null`, solange keiner abgeschlossen ist. */
  readonly importOutcome = signal<ImportOutcome | null>(null);

  /** `true`, während eine Datei über der Dropzone schwebt. */
  readonly dragActive = signal(false);

  /**
   * Die Datei, für die das Backend ein Duplikat gemeldet hat — solange gesetzt, steht der
   * Bestätigungsdialog offen. `null`, sobald der User entschieden hat.
   */
  readonly duplicateFile = signal<File | null>(null);

  /**
   * Die Buchungen des soeben abgeschlossenen Imports, oder `null`, solange es keine gibt
   * (FE-PDF-04).
   *
   * <p>`null` ist der Normalzustand vor dem ersten Import und nach einem Fehlschlag; eine
   * *leere* Liste ist es nie: Der Nullfall (Auszug ohne Buchungen) fragt gar nicht erst nach,
   * eine leere Antwort trotz `total > 0` wird zum Hinweis ({@link listEmptyMessage}), und das
   * Template zeigt eine leere Liste auch dann nicht an.
   *
   * <p>Der Inhalt gehört ausschliesslich zu diesem einen Job — die Einschränkung kommt vom
   * Endpoint, der über Job-ID *und* eingeloggten User abfragt, nicht aus einem Filter hier.
   */
  readonly importedTransactions = signal<Transaction[] | null>(null);

  /** Meldung, wenn die Liste nicht geladen werden konnte — der Import selbst blieb erfolgreich. */
  readonly listErrorMessage = signal<string | null>(null);

  /** Meldung, wenn eine Kategorie-Korrektur nicht gespeichert werden konnte. */
  readonly saveErrorMessage = signal<string | null>(null);

  /**
   * Hinweis, wenn der Import zwar `DONE` mit `total > 0` ist, die Liste aber leer zurückkommt
   * (FE-NOTIF-05) — {@link importedTransactions} bleibt dann `null`, damit die Invariante «eine
   * leere Liste ist es nie» weiter gilt. Siehe {@link LIST_EMPTY_MESSAGE}, warum das praktisch
   * nicht vorkommt.
   */
  readonly listEmptyMessage = signal<string | null>(null);

  /**
   * Zähler der Import-Läufe — steigt mit jedem {@link clearImportedTransactions} um eins.
   *
   * <p>Er schliesst das Zeitfenster zwischen Anfrage und Antwort: Wählt der Nutzer eine neue
   * Datei, während `GET /api/import/{jobId}/transactions` des vorigen Jobs noch offen ist, räumt
   * das Aufräumen die Liste weg — und die verspätete Antwort setzte sie ohne diesen Zähler
   * danach wieder, unter den Fortschrittsbalken oder die Fehlermeldung des neuen Uploads.
   *
   * <p>Der Zähler hängt bewusst am Aufräumen und nicht an der Job-ID: Eine client-seitig
   * abgelehnte Datei hat gar keine, macht die Liste aber genauso ungültig.
   */
  private importRun = 0;

  /**
   * Der Job, dessen ID die Komponente zuletzt selbst in die URL geschrieben hat oder aus ihr
   * übernommen hat. Die Wache in {@link syncFromUrl}: Nach dem Upload navigiert die Komponente
   * auf `?job=<neu>`, und die eigene `queryParamMap`-Subscription sieht das — ohne diese Wache
   * pollte sie den Job, den {@link upload} gerade schon verfolgt, ein zweites Mal.
   */
  private trackedJobId: number | null = null;

  /**
   * Der laufende Status-Poll. Wird ein anderer Job geöffnet, während einer läuft — Glocke
   * während eines Imports, Browser-Zurück auf ein früheres `?job=` —, muss der alte Poll
   * aufhören: zwei Polls schrieben sonst abwechselnd in dieselben Signals, und der langsamere
   * gewänne.
   */
  private pollSubscription: Subscription | null = null;

  constructor() {
    // `queryParamMap` liefert den aktuellen Stand sofort und danach jede Änderung — Deep-Link
    // aus der Glocke, Reload, Browser-Zurück auf ein früheres `?job=`. Dieselbe Mechanik wie
    // `?month=` in `CategoryOverview`.
    this.route.queryParamMap
      .pipe(takeUntilDestroyed())
      .subscribe((params) => this.syncFromUrl(params));
  }

  onDragOver(event: DragEvent): void {
    // Ohne preventDefault löst der Browser das drop-Event nicht aus.
    event.preventDefault();
    if (!this.uploading()) {
      this.dragActive.set(true);
    }
  }

  onDragLeave(): void {
    this.dragActive.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragActive.set(false);
    if (this.uploading()) {
      return;
    }
    this.selectFile(Array.from(event.dataTransfer?.files ?? []));
  }

  onFilePicked(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectFile(Array.from(input.files ?? []));
    // Reset, damit dieselbe Datei nach einer Korrektur erneut wählbar ist.
    input.value = '';
  }

  /** «Trotzdem importieren»: derselbe Upload noch einmal, diesmal mit Force-Flag. */
  confirmDuplicateImport(): void {
    const file = this.duplicateFile();
    this.duplicateFile.set(null);
    if (file) {
      this.upload(file, true);
    }
  }

  /** «Abbrechen»: Dialog zu, kein Import — der Grund bleibt als Meldung stehen. */
  cancelDuplicateImport(): void {
    this.duplicateFile.set(null);
    this.importOutcome.set({ kind: 'error', message: DUPLICATE_MESSAGE });
  }

  private selectFile(files: File[]): void {
    this.errorMessage.set(null);
    this.importOutcome.set(null);
    this.progress.set(null);
    this.duplicateFile.set(null);
    // Zusammen mit der Erfolgsmeldung, nicht erst beim Upload: Eine abgelehnte Datei
    // (kein PDF, zu gross) kommt gar nicht bis `upload()` — die Liste des vorigen Imports
    // stünde dann ohne ihre Meldung unter einer Fehlermeldung und sähe aus, als gehörte sie
    // zu der Datei, die eben zurückgewiesen wurde.
    this.clearImportedTransactions();

    const file = files[0];
    if (!file) {
      return;
    }
    if (files.length > 1) {
      this.errorMessage.set('Bitte lade nur eine Datei aufs Mal hoch.');
      return;
    }
    if (!PdfUpload.isPdf(file)) {
      this.errorMessage.set('Nur PDF-Dateien werden unterstützt.');
      return;
    }
    if (file.size > MAX_PDF_BYTES) {
      this.errorMessage.set('Maximale Dateigrösse: 10 MB');
      return;
    }
    this.upload(file);
  }

  private upload(file: File, force = false): void {
    this.uploading.set(true);
    this.progress.set(null);
    // Noch einmal, obwohl `selectFile` es bereits getan hat: `confirmDuplicateImport` springt
    // direkt hierher und nimmt den Weg über die Dateiauswahl gar nicht (FE-PDF-04).
    this.clearImportedTransactions();
    this.importService
      .importPdf(file, force)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (started) => {
          // Ab hier ist das PDF gelesen und die Anzahl bekannt — der Balken hat seinen Nenner.
          this.progress.set({ processed: 0, total: started.total });
          this.rememberJobInUrl(started.jobId);
          if (started.total === 0) {
            // Erkannter Auszug ohne Buchungen (BE-PDF-05): Es gibt keinen Lauf zu verfolgen.
            this.finish({ kind: 'success', count: 0, degraded: false });
            return;
          }
          this.trackJob(started.jobId);
        },
        error: (error: unknown) => {
          this.uploading.set(false);
          this.progress.set(null);
          // Das Duplikat ist kein Endzustand, sondern eine Rückfrage: statt einer Meldung
          // öffnet es den Dialog. Nur im Force-Lauf kann es das nicht mehr sein — dort ist der
          // Duplikatcheck übersprungen, ein 409 also gar nicht möglich.
          if (error instanceof HttpErrorResponse && error.status === 409) {
            this.duplicateFile.set(file);
            return;
          }
          this.importOutcome.set({ kind: 'error', message: PdfUpload.importErrorMessage(error) });
        },
      });
  }

  /**
   * Lädt die Glocke neu, damit eine im Import entstandene Abo-Benachrichtigung sofort sichtbar
   * ist (siehe Klassen-Doc). Ein Fehler bleibt still wie bei jedem anderen Reload der Glocke
   * (`NotificationBell.reload`): der Import selbst ist gelungen, und die Glocke zeigt weiterhin
   * den letzten bekannten Stand.
   */
  private reloadNotifications(): void {
    this.notificationService
      .reload()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: (_err: HttpErrorResponse) => {
          // Siehe Methoden-Doc: bewusst ohne Meldung.
        },
      });
  }

  /**
   * Verfolgt den Hintergrundlauf bis zum Endzustand und schreibt den Fortschritt fort — nach
   * einem Upload wie für einen über `?job=` übergebenen Job ({@link openJob}).
   *
   * <p>Ein noch laufender Poll eines anderen Jobs wird zuerst beendet ({@link pollSubscription}).
   */
  private trackJob(jobId: number): void {
    this.pollSubscription?.unsubscribe();
    this.pollSubscription = this.importService
      .pollJob(jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status: ImportJobStatusResponse) => {
          this.progress.set({ processed: status.processed, total: status.total });
          if (status.status === 'DONE') {
            this.finish({ kind: 'success', count: status.total, degraded: status.degraded });
            // Erst jetzt, nie vorher: Vor `DONE` antwortet der Endpoint mit 409 (BE-PDF-14).
            // Nach einem Upload kommt der Nullfall hier nicht an — er verlässt `upload()` schon
            // vor `trackJob`. Über `?job=` schon: Ein Auszug ohne Buchungen wird als `DONE` mit
            // `total = 0` persistiert, und die Liste bliebe leer — die das Template nie zeigt.
            if (status.total > 0) {
              this.loadImportedTransactions(jobId);
            }
            // Auch für einen über `?job=` geöffneten, längst fertigen Job — dort ist der Reload
            // redundant (die Glocke lud bei `NavigationEnd`), für einen per Reload
            // wiederaufgenommenen `RUNNING`-Job aber nötig. Ein GET ist billiger als die
            // Fallunterscheidung.
            this.reloadNotifications();
          } else if (status.status === 'FAILED') {
            this.finish({ kind: 'error', message: JOB_FAILED_MESSAGE });
          }
        },
        // Bricht die Statusabfrage selbst ab (Netzwerk, 404), ist der Ausgang des Imports
        // unbekannt. Ihn als Erfolg zu melden wäre die schlechtere Lüge: Der Nutzer prüft dann
        // nicht nach.
        error: (error: unknown) =>
          this.finish({ kind: 'error', message: PdfUpload.pollErrorMessage(error) }),
      });
  }

  /**
   * Nimmt einen über `?job=` übergebenen Job auf (FE-NOTIF-05) — aus der Glocke, per Reload
   * oder Browser-Zurück.
   *
   * <p>Räumt denselben Zustand weg wie {@link selectFile} vor einem Upload: Was noch vom vorigen
   * Import auf der Seite steht, gehört nicht zu diesem Job. `uploading` sperrt die Dropzone, bis
   * der erste Poll antwortet — sonst könnte in dieser Lücke ein Upload starten, dessen Poll mit
   * diesem hier um die Signals stritte. Der Endzustand kommt mit dem ersten Poll (`timer(0)`),
   * der Spinner ist also nur für einen Roundtrip zu sehen.
   */
  private openJob(jobId: number): void {
    this.trackedJobId = jobId;
    this.errorMessage.set(null);
    this.importOutcome.set(null);
    this.duplicateFile.set(null);
    this.clearImportedTransactions();
    this.uploading.set(true);
    this.progress.set(null);
    this.trackJob(jobId);
  }

  /**
   * Übernimmt den Job aus der URL — beim Erstladen und bei jeder äusseren Änderung.
   *
   * <p>Ohne Parameter bleibt die Seite, wie sie ist: Sie startet leer, und ein Browser-Zurück
   * oder der Sidebar-Link auf die parameterlose Adresse lässt stehen, was gerade sichtbar ist.
   * Nur die Wache wird gelöst — sonst schluckte sie den nächsten Klick auf dieselbe
   * Benachrichtigung, obwohl die URL sich dann echt ändert (Review-Befund #349). Ein unbrauchbarer
   * Wert (keine positive Ganzzahl) wird ignoriert und per `replaceUrl` aus der Adresse genommen,
   * damit die URL nicht etwas behauptet, was die Seite nicht zeigt — wie
   * `CategoryOverview.syncFromUrl` bei einem kaputten `?month=`. Die Wache gegen
   * {@link trackedJobId} fängt das Echo der eigenen Navigation aus {@link rememberJobInUrl} ab.
   */
  private syncFromUrl(params: ParamMap): void {
    const raw = params.get('job');
    if (raw === null) {
      this.trackedJobId = null;
      return;
    }
    const jobId = PdfUpload.parseJobId(raw);
    if (jobId === null) {
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { job: null },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
      return;
    }
    if (jobId === this.trackedJobId) {
      return;
    }
    this.openJob(jobId);
  }

  /**
   * Schreibt die Job-ID des soeben gestarteten Imports in die URL, damit ein Reload dieselbe
   * Übersicht zeigt, die gleich sichtbar sein wird (FE-NOTIF-05). Ein neuer Verlaufseintrag,
   * kein `replaceUrl`: Browser-Zurück führt so zur Übersicht des vorigen Imports, wie der
   * Monats-Stepper in `CategoryOverview` zum vorigen Monat.
   */
  private rememberJobInUrl(jobId: number): void {
    this.trackedJobId = jobId;
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { job: jobId },
      queryParamsHandling: 'merge',
    });
  }

  /**
   * Lädt die Buchungen des abgeschlossenen Imports nach (FE-PDF-04).
   *
   * <p>Ein Fehlschlag bleibt bewusst folgenlos für den Ausgang des Imports: {@link importOutcome}
   * steht bereits auf Erfolg und bleibt dort. Die Buchungen sind gespeichert — scheitert nur
   * ihre Anzeige, wäre es eine Lüge, daraus einen gescheiterten Import zu machen.
   *
   * <p>Eine Antwort, die erst nach dem nächsten Aufräumen eintrifft, wird verworfen — sie gehört
   * zu einem Import, den der Nutzer bereits hinter sich gelassen hat ({@link importRun}).
   *
   * <p>Eine leere Antwort bei `total > 0` landet als Hinweis ({@link LIST_EMPTY_MESSAGE}), nicht
   * als leere Liste — {@link importedTransactions} bleibt `null`. Praktisch unerreichbar, siehe
   * dort; die Wache gilt der Invariante.
   */
  private loadImportedTransactions(jobId: number): void {
    const run = this.importRun;
    this.importService
      .importTransactions(jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (transactions) => {
          if (run !== this.importRun) {
            return;
          }
          if (transactions.length === 0) {
            this.listEmptyMessage.set(LIST_EMPTY_MESSAGE);
          } else {
            this.importedTransactions.set(transactions);
          }
        },
        error: () => {
          if (run === this.importRun) {
            this.listErrorMessage.set(LIST_FAILED_MESSAGE);
          }
        },
      });
  }

  /**
   * Setzt die Kategorie einer importierten Buchung (FE-PDF-04, AC 2 und 3).
   *
   * <p>Optimistisch wie in der Kategorie-Übersicht ({@code CategoryOverview.changeCategory}):
   * der neue Wert steht sofort im Signal und damit im DOM, erst danach läuft der PUT. Scheitert
   * er, kommt der alte Wert zurück und eine Meldung erscheint — die Anzeige behauptet nie einen
   * Stand, den der Server nicht hat.
   *
   * <p>Anders als dort folgt <em>kein</em> Nachladen: Dieser Screen zeigt eine flache Liste ohne
   * Summen, Anteile oder Donut, die nach einer Korrektur nicht mehr zu den Zeilen darunter
   * passen könnten. Der serverseitige Lerneffekt (BE-CAT-04 erweitert die Lookup-Tabelle) hängt
   * am PUT und nicht an einem erneuten GET.
   */
  changeCategory(transaction: Transaction, category: string): void {
    const previous = transaction.category;
    if (previous === category) {
      return;
    }

    this.saveErrorMessage.set(null);
    this.applyCategory(transaction.id, category);

    this.transactionService
      .updateCategory(transaction.id, category)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        error: () => {
          this.applyCategory(transaction.id, previous);
          this.saveErrorMessage.set(CATEGORY_SAVE_FAILED_MESSAGE);
        },
      });
  }

  /** Räumt die Liste des vorigen Imports samt ihrer Meldungen weg (AC 7). */
  private clearImportedTransactions(): void {
    // Entwertet jede noch offene Antwort auf die Buchungen des vorigen Jobs.
    this.importRun++;
    this.importedTransactions.set(null);
    this.listErrorMessage.set(null);
    this.saveErrorMessage.set(null);
    this.listEmptyMessage.set(null);
  }

  /** Ersetzt die Kategorie einer Buchung in der Liste (neue Objekte wegen OnPush). */
  private applyCategory(transactionId: number, category: string): void {
    this.importedTransactions.update((current) =>
      current === null
        ? current
        : current.map((tx) => (tx.id === transactionId ? { ...tx, category } : tx)),
    );
  }

  /**
   * Der Betrag mit Vorzeichen, wie ihn {@code <app-amount>} erwartet.
   *
   * <p>{@link Transaction.betrag} ist eine positive Magnitude, die Richtung steht in
   * {@link Transaction.income}. Anders als die Kategorie-Übersicht, die nur Ausgaben zeigt,
   * listet `GET /api/import/{jobId}/transactions` den ganzen Import inklusive Gutschriften —
   * ohne Vorzeichen wäre eine Rückerstattung in dieser Liste von einer Belastung nicht zu
   * unterscheiden. Die gemeinsame Komponente trägt die Richtung im sichtbaren `+`/`−` und im
   * `aria-label`, nicht bloss in der Farbe.
   *
   * <p>{@link Transaction.directionUncertain} bleibt hier ohne Wirkung: Eine bloss angenommene
   * Richtung (BE-PDF-10) erscheint als das, was das Backend gespeichert hat — eine Belastung.
   * Korrigiert wird sie weiterhin nur in der Prüfliste der Kategorie-Übersicht; ein zweiter Ort
   * für dieselbe Entscheidung wäre ein zweiter Ort zum Auseinanderlaufen.
   */
  signedAmount(tx: Transaction): number {
    return tx.income ? tx.betrag : -tx.betrag;
  }

  /**
   * Barrierefreie Beschriftung des Dropdowns — Buchungstext plus Gegenpartei.
   *
   * <p>`\n` aus {@link Transaction.buchungsdetails} wird zu «, »: Der Text landet in einem
   * Attribut, ein `\n` darin wäre für die Ausgabe bloss ein Leerzeichen ohne Pause. Gleiche
   * Begründung wie bei {@code CategoryOverview.transactionLabel}.
   */
  transactionLabel(tx: Transaction): string {
    return tx.buchungsdetails
      ? `${tx.buchungstext}, ${tx.buchungsdetails.replaceAll('\n', ', ')}`
      : tx.buchungstext;
  }

  private finish(outcome: ImportOutcome): void {
    this.uploading.set(false);
    this.progress.set(null);
    this.importOutcome.set(outcome);
  }

  /**
   * Erfolgsmeldung mit Anzahl — «42 Transaktionen erkannt», Singular bei genau einer.
   *
   * <p>Der Nullfall (BE-PDF-05: erkannter Auszug ohne Buchungen → `200 {count: 0}`) bekommt
   * eine eigene Formulierung: ein blosses «0 Transaktionen erkannt.» liesse offen, ob das
   * Konto ohne Bewegung war oder das falsche PDF hochgeladen wurde.
   */
  successMessage(outcome: { count: number; degraded: boolean }): string {
    if (outcome.count === 0) {
      return (
        'Keine Transaktionen erkannt. Der Kontoauszug wurde gelesen, enthält aber keine ' +
        'Buchungen — falls du Bewegungen erwartest, prüfe, ob es das richtige PDF ist.'
      );
    }
    const base =
      outcome.count === 1 ? '1 Transaktion erkannt.' : `${outcome.count} Transaktionen erkannt.`;
    // Der Import ist auch im degradierten Fall vollständig gespeichert — die Meldung bleibt
    // deshalb eine Erfolgsmeldung und wird nur ergänzt (BE-PDF-09, AC2).
    return outcome.degraded ? base + DEGRADED_HINT : base;
  }

  /** Begleittext zum Balken: «45 von 108 Transaktionen kategorisiert». */
  progressLabel(current: ImportProgress): string {
    return `${current.processed} von ${current.total} Transaktionen kategorisiert`;
  }

  /**
   * Mappt den Backend-Fehler auf eine Nutzermeldung (FE-PDF-02, MISSING_TEXT_LAYER seit BE-PDF-08).
   *
   * <p>Die drei 400er unterscheidet der `reason` im Body (`ImportErrorResponse.java`);
   * ein 400 ohne bekannten `reason` (z. B. fehlender file-Part) fällt auf die Format-Meldung.
   * 408 trägt den Retry-Hinweis; 413 hat seit BE-PDF-08 eine eigene Meldung (serverseitiges
   * 10-MB-Limit, `PdfImportController`); alles Übrige bleibt generisch.
   *
   * <p>Der 413 ist über die UI nicht erreichbar: `selectFile` prüft die Grösse gegen
   * `MAX_PDF_BYTES` und ist der einzige Pfad zu `upload()`. Die Meldung ist bewusst ein Netz für
   * einen 413 aus anderer Quelle (z. B. Reverse Proxy) — sie schliesst keine Nutzerlücke.
   *
   * <p>Der 409 landet im Normalfall gar nicht hier — er öffnet den Dialog. Die Meldung greift
   * für den abgebrochenen Dialog und als Netz für einen 409 aus einer anderen Quelle; sie trägt
   * deshalb **keinen** Retry-Hinweis: bei einem Duplikat scheitert ein zweiter Versuch identisch.
   */
  private static importErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) {
        const reason = (error.error as Partial<ImportErrorResponse> | null)?.reason;
        if (reason === 'PASSWORD_PROTECTED') {
          return 'Das PDF ist passwortgeschützt. Bitte entferne das Passwort und lade es erneut hoch.';
        }
        if (reason === 'MISSING_TEXT_LAYER') {
          return 'Das PDF enthält keinen Text (vermutlich ein Scan). Bitte lade den Original-Kontoauszug aus dem E-Banking herunter, statt ihn zu scannen.';
        }
        return 'Das PDF konnte nicht als Kontoauszug gelesen werden. Bitte lade den Original-Kontoauszug deiner Bank hoch.';
      }
      if (error.status === 408) {
        return 'Der Import hat zu lange gedauert und wurde abgebrochen. Bitte versuche es erneut.';
      }
      if (error.status === 409) {
        return DUPLICATE_MESSAGE;
      }
      if (error.status === 413) {
        return 'Das PDF ist zu gross (max. 10 MB). Bitte lade eine kleinere Datei hoch.';
      }
    }
    return 'Der Import ist fehlgeschlagen — bitte versuche es erneut.';
  }

  /**
   * Mappt einen Abbruch der Statusabfrage auf eine Nutzermeldung.
   *
   * <p>Der 404 bekommt seit FE-NOTIF-05 eine eigene Meldung: Über `?job=` kann ein Job gemeint
   * sein, den es für diesen Nutzer nicht (mehr) gibt — «fehlgeschlagen, bitte erneut versuchen»
   * wäre dann falsch, es gibt nichts zu wiederholen. Nach einem Upload ist ein 404 des Polls
   * praktisch unerreichbar (der Job wurde eben angelegt); die Meldung stimmte auch dann.
   */
  private static pollErrorMessage(error: unknown): string {
    if (error instanceof ImportPollTimeoutError) {
      return POLL_TIMEOUT_MESSAGE;
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      return JOB_NOT_FOUND_MESSAGE;
    }
    return PdfUpload.importErrorMessage(error);
  }

  /**
   * Liest eine Job-ID aus `?job=` — nur eine positive Ganzzahl in Dezimalschreibweise gilt.
   * `Number('')`, `Number('1e3')` oder `Number(' 7 ')` wären sonst alle Zahlen; das Backend
   * kennt aber nur fortlaufende IDs ab 1.
   */
  private static parseJobId(raw: string): number | null {
    if (!/^[1-9]\d*$/.test(raw)) {
      return null;
    }
    const jobId = Number(raw);
    return Number.isSafeInteger(jobId) ? jobId : null;
  }

  /** Drag-and-Drop liefert den MIME-Type nicht zuverlässig — Dateiendung als Fallback. */
  private static isPdf(file: File): boolean {
    return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  }
}
