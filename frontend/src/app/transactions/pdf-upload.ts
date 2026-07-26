import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { Notice } from '../shared/notice/notice';
import { PdfImportService } from './pdf-import.service';

/** Serverseitiges Upload-Limit aus BE-PDF-03 — client-seitig vorab geprüft (US-04). */
const MAX_PDF_BYTES = 10 * 1024 * 1024;

/**
 * PDF-Upload für den Kontoauszug-Import (FE-PDF-01, US-04).
 *
 * <p>Dropzone mit Drag-and-Drop plus File-Picker als tastaturbedienbare
 * Alternative. Vor dem Upload wird client-seitig validiert (nur `.pdf`,
 * max. 10 MB); während `POST /import/pdf` läuft, zeigt die Dropzone einen
 * Spinner und nimmt keine weiteren Dateien an.
 *
 * <p>Der Ausgang des Imports wird hier nur generisch gehalten
 * ({@link importOutcome}) — die differenzierte Ergebnis-Anzeige
 * (Erfolgs-Count, 400/408/409-Meldungen) kommt mit FE-PDF-02 (#28).
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

  /** Generischer Ausgang des letzten Uploads; Details folgen mit FE-PDF-02. */
  readonly importOutcome = signal<'success' | 'error' | null>(null);

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
        next: () => {
          this.uploading.set(false);
          this.importOutcome.set('success');
        },
        error: () => {
          this.uploading.set(false);
          this.importOutcome.set('error');
        },
      });
  }

  /** Drag-and-Drop liefert den MIME-Type nicht zuverlässig — Dateiendung als Fallback. */
  private static isPdf(file: File): boolean {
    return file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  }
}
