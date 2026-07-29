import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Notice } from '../shared/notice/notice';
import { ImportErrorResponse } from './import-error.model';
import { PdfImportService } from './pdf-import.service';

/** Serverseitiges Upload-Limit aus BE-PDF-03 — client-seitig vorab geprüft (US-04). */
const MAX_PDF_BYTES = 10 * 1024 * 1024;

/** Ausgang des letzten Uploads — Erfolg mit Anzahl oder Fehler mit fertiger Nutzermeldung. */
export type ImportOutcome =
  | { kind: 'success'; count: number }
  | { kind: 'error'; message: string };

/**
 * PDF-Upload für den Kontoauszug-Import (FE-PDF-01/FE-PDF-02, US-04).
 *
 * <p>Dropzone mit Drag-and-Drop plus File-Picker als tastaturbedienbare
 * Alternative. Vor dem Upload wird client-seitig validiert (nur `.pdf`,
 * max. 10 MB); während `POST /import/pdf` läuft, zeigt die Dropzone einen
 * Spinner und nimmt keine weiteren Dateien an.
 *
 * <p>Der Ausgang landet differenziert in {@link importOutcome}: Erfolg trägt
 * die Anzahl importierter Transaktionen, Fehler eine bereits formulierte
 * Meldung ({@link PdfUpload.importErrorMessage} mappt Status + `reason` des
 * Backends). Der 409-Duplikatfall bleibt bis FE-PDF-03 (#29) im generischen
 * Fallback.
 */
@Component({
  selector: 'app-pdf-upload',
  imports: [Button, Card, Notice],
  templateUrl: './pdf-upload.html',
  styleUrl: './pdf-upload.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PdfUpload {
  private readonly importService = inject(PdfImportService);
  private readonly destroyRef = inject(DestroyRef);

  /** `true`, solange der Upload läuft — zeigt den Spinner und sperrt die Dropzone. */
  readonly uploading = signal(false);

  /** Client-seitige Validierungsmeldung oder `null`. */
  readonly errorMessage = signal<string | null>(null);

  /** Ausgang des letzten Uploads oder `null`, solange keiner abgeschlossen ist. */
  readonly importOutcome = signal<ImportOutcome | null>(null);

  /** `true`, während eine Datei über der Dropzone schwebt. */
  readonly dragActive = signal(false);

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

  private selectFile(files: File[]): void {
    this.errorMessage.set(null);
    this.importOutcome.set(null);

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

  private upload(file: File): void {
    this.uploading.set(true);
    this.importService
      .importPdf(file)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.uploading.set(false);
          this.importOutcome.set({ kind: 'success', count: response.count });
        },
        error: (error: unknown) => {
          this.uploading.set(false);
          this.importOutcome.set({ kind: 'error', message: PdfUpload.importErrorMessage(error) });
        },
      });
  }

  /** Erfolgsmeldung mit Anzahl — «42 Transaktionen erkannt», Singular bei genau einer. */
  successMessage(count: number): string {
    return count === 1 ? '1 Transaktion erkannt.' : `${count} Transaktionen erkannt.`;
  }

  /**
   * Mappt den Backend-Fehler auf eine Nutzermeldung (FE-PDF-02).
   *
   * <p>Die beiden 400er unterscheidet der `reason` im Body (`ImportErrorResponse.java`);
   * ein 400 ohne bekannten `reason` (z. B. fehlender file-Part) fällt auf die Format-Meldung.
   * 408 trägt den Retry-Hinweis; alles Übrige — inkl. 409-Duplikat bis FE-PDF-03 (#29) und
   * 413 — bleibt generisch.
   */
  private static importErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 400) {
        const reason = (error.error as Partial<ImportErrorResponse> | null)?.reason;
        return reason === 'PASSWORD_PROTECTED'
          ? 'Das PDF ist passwortgeschützt. Bitte entferne das Passwort und lade es erneut hoch.'
          : 'Das PDF konnte nicht als Kontoauszug gelesen werden. Bitte lade den Original-Kontoauszug deiner Bank hoch.';
      }
      if (error.status === 408) {
        return 'Der Import hat zu lange gedauert und wurde abgebrochen. Bitte versuche es erneut.';
      }
    }
    return 'Der Import ist fehlgeschlagen — bitte versuche es erneut.';
  }

  /** Drag-and-Drop liefert den MIME-Type nicht zuverlässig — Dateiendung als Fallback. */
  private static isPdf(file: File): boolean {
    return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  }
}
