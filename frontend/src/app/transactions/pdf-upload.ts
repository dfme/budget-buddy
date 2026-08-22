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

import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Meter } from '../shared/meter/meter';
import { Modal } from '../shared/modal/modal';
import { Notice } from '../shared/notice/notice';
import { ImportErrorResponse } from './import-error.model';
import { ImportJobStatusResponse } from './import-response.model';
import { PdfImportService } from './pdf-import.service';

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
 * <p><strong>Zweistufig seit BE-PDF-09 / ADR-13:</strong> `POST /api/import/pdf`
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
 */
@Component({
  selector: 'app-pdf-upload',
  imports: [Button, Card, Meter, Modal, Notice],
  templateUrl: './pdf-upload.html',
  styleUrl: './pdf-upload.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfUpload {
  private readonly importService = inject(PdfImportService);
  private readonly destroyRef = inject(DestroyRef);

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
    this.importService
      .importPdf(file, force)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (started) => {
          // Ab hier ist das PDF gelesen und die Anzahl bekannt — der Balken hat seinen Nenner.
          this.progress.set({ processed: 0, total: started.total });
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

  /** Verfolgt den Hintergrundlauf bis zum Endzustand und schreibt den Fortschritt fort. */
  private trackJob(jobId: number): void {
    this.importService
      .pollJob(jobId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (status: ImportJobStatusResponse) => {
          this.progress.set({ processed: status.processed, total: status.total });
          if (status.status === 'DONE') {
            this.finish({ kind: 'success', count: status.total, degraded: status.degraded });
          } else if (status.status === 'FAILED') {
            this.finish({ kind: 'error', message: JOB_FAILED_MESSAGE });
          }
        },
        // Bricht die Statusabfrage selbst ab (Netzwerk, 404), ist der Ausgang des Imports
        // unbekannt. Ihn als Erfolg zu melden wäre die schlechtere Lüge: Der Nutzer prüft dann
        // nicht nach.
        error: (error: unknown) =>
          this.finish({ kind: 'error', message: PdfUpload.importErrorMessage(error) }),
      });
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

  /** Drag-and-Drop liefert den MIME-Type nicht zuverlässig — Dateiendung als Fallback. */
  private static isPdf(file: File): boolean {
    return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  }
}
