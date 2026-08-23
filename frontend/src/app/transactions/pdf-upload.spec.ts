import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportJobStatusResponse } from './import-response.model';
import { PdfUpload } from './pdf-upload';

/** Job-ID, die das Backend-Double in allen Tests zurückgibt. */
const JOB_ID = 7;

function pdfFile(name = 'kontoauszug.pdf'): File {
  return new File(['%PDF-1.4'], name, { type: 'application/pdf' });
}

/** jsdom kennt keinen DataTransfer-Konstruktor — minimales Event-Double genügt. */
function dropEvent(files: File[]): DragEvent {
  return {
    preventDefault: () => undefined,
    dataTransfer: { files },
  } as unknown as DragEvent;
}

describe('PdfUpload', () => {
  let fixture: ComponentFixture<PdfUpload>;
  let component: PdfUpload;
  let httpMock: HttpTestingController;

  /** Klickt die Aktion des Duplikat-Dialogs mit dieser Beschriftung. */
  function clickModalButton(label: string): void {
    const button = Array.from<HTMLButtonElement>(
      fixture.nativeElement.querySelectorAll('app-modal .modal__actions button'),
    ).find((btn) => btn.textContent?.trim() === label);
    if (!button) {
      throw new Error(`Kein Dialog-Button mit der Beschriftung "${label}"`);
    }
    button.click();
  }

  /**
   * Beantwortet den Upload und danach den ersten Status-Poll — der vollständige zweistufige
   * Import (ADR-13). Ohne den zweiten Schritt bliebe die Komponente im Fortschrittszustand
   * stehen, denn `POST /api/import/pdf` meldet seit BE-PDF-09 nur noch den Start.
   */
  function completeImport(total: number, patch: Partial<ImportJobStatusResponse> = {}): void {
    httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total });
    if (total === 0) {
      // Erkannter Auszug ohne Buchungen: Es gibt keinen Lauf zu verfolgen (BE-PDF-05).
      fixture.detectChanges();
      return;
    }
    // Unter Faketimern braucht auch die 0-Verzögerung des ersten Polls einen Tick.
    vi.advanceTimersByTime(1);
    httpMock.expectOne(`/api/import/${JOB_ID}/status`).flush({
      status: 'DONE',
      total,
      processed: total,
      degraded: false,
      ...patch,
    });
    fixture.detectChanges();
  }

  beforeEach(async () => {
    // Vitest-Faketimer statt fakeAsync/tick: Das Projekt läuft zoneless, zone-testing.js ist
    // deshalb gar nicht geladen. Der Poll-Takt aus PdfImportService hängt an einem Timer.
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [PdfUpload],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(PdfUpload);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('shows the spinner while parsing and the progress bar while categorizing', () => {
    component.onDrop(dropEvent([pdfFile()]));
    fixture.detectChanges();

    // Phase 1 — das PDF wird geparst: Es gibt noch keinen Nenner, also nur den Spinner.
    expect(component.uploading()).toBe(true);
    expect(fixture.nativeElement.querySelector('.spinner')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('app-meter')).toBeNull();

    const req = httpMock.expectOne('/api/import/pdf');
    expect(req.request.method).toBe('POST');
    req.flush({ jobId: JOB_ID, total: 42 });
    fixture.detectChanges();

    // Phase 2 — die Kategorisierung läuft: Der Balken ersetzt den Spinner und kennt seinen Nenner.
    expect(component.uploading()).toBe(true);
    expect(fixture.nativeElement.querySelector('.spinner')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-meter')).not.toBeNull();
    expect(component.progress()).toEqual({ processed: 0, total: 42 });

    vi.advanceTimersByTime(1);
    httpMock.expectOne(`/api/import/${JOB_ID}/status`).flush({
      status: 'RUNNING',
      total: 42,
      processed: 20,
      degraded: false,
    });
    fixture.detectChanges();

    expect(component.progressPercent()).toBe(48);
    expect(fixture.nativeElement.textContent).toContain('20 von 42 Transaktionen kategorisiert');

    vi.advanceTimersByTime(700);
    httpMock.expectOne(`/api/import/${JOB_ID}/status`).flush({
      status: 'DONE',
      total: 42,
      processed: 42,
      degraded: false,
    });
    fixture.detectChanges();

    expect(component.uploading()).toBe(false);
    expect(component.importOutcome()).toEqual({ kind: 'success', count: 42, degraded: false });
    expect(fixture.nativeElement.querySelector('app-meter')).toBeNull();
  });

  /**
   * AC2 aus #192: Ein Import, der serverseitig ins Zeitbudget lief, ist trotzdem vollständig
   * gespeichert. Die Meldung bleibt deshalb eine Erfolgsmeldung und erklärt nur, warum ein Teil
   * unter «Sonstiges» steht.
   */
  it('reports a degraded import as a success with an explanation', () => {
    component.onDrop(dropEvent([pdfFile()]));
    completeImport(108, { degraded: true });

    expect(component.importOutcome()).toEqual({ kind: 'success', count: 108, degraded: true });
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.getAttribute('role')).toBe('status');
    expect(notice?.textContent).toContain('108 Transaktionen erkannt.');
    expect(notice?.textContent).toContain('nicht automatisch kategorisiert');
  });

  it('shows an error when the background job fails', () => {
    component.onDrop(dropEvent([pdfFile()]));
    completeImport(12, { status: 'FAILED', processed: 5 });

    expect(component.uploading()).toBe(false);
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.getAttribute('role')).toBe('alert');
    expect(notice?.textContent).toContain('Der Import ist fehlgeschlagen');
  });

  it('shows an error when the status request itself fails', () => {
    component.onDrop(dropEvent([pdfFile()]));
    httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 12 });

    vi.advanceTimersByTime(1);
    httpMock
      .expectOne(`/api/import/${JOB_ID}/status`)
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    // Ein unbekannter Ausgang darf nicht als Erfolg durchgehen — der Nutzer prüfte sonst nicht nach.
    expect(component.uploading()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-notice')?.getAttribute('role')).toBe('alert');
  });

  it('tells the user to reload when the job never leaves RUNNING', () => {
    // Der Ausgang ist hier weder Erfolg noch Fehlschlag: Der Import kann durchgelaufen sein,
    // während nur die Anzeige den Anschluss verloren hat. «Erneut versuchen» wäre der falsche
    // Rat — er erzeugte womöglich eine Dublette.
    component.onDrop(dropEvent([pdfFile()]));
    httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 12 });

    for (let elapsed = 0; elapsed < 30 * 60 * 1000; elapsed += 700) {
      vi.advanceTimersByTime(elapsed === 0 ? 1 : 700);
      const pending = httpMock.match(`/api/import/${JOB_ID}/status`);
      if (pending.length === 0) {
        break;
      }
      for (const request of pending) {
        request.flush({ status: 'RUNNING', total: 12, processed: 3, degraded: false });
      }
    }
    fixture.detectChanges();

    expect(component.uploading()).toBe(false);
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.getAttribute('role')).toBe('alert');
    expect(notice?.textContent).toContain('Status ist unbekannt');
    expect(notice?.textContent).toContain('Seite neu');
    // Nicht als Fehlschlag ausgeben — das wäre eine Aussage, die wir nicht belegen können.
    expect(notice?.textContent).not.toContain('fehlgeschlagen');
  });

  it('uploads a file selected via the file picker', () => {
    const input = { files: [pdfFile()], value: 'C:\\fakepath\\kontoauszug.pdf' };
    component.onFilePicked({ target: input } as unknown as Event);

    completeImport(3);

    expect(component.importOutcome()).toEqual({ kind: 'success', count: 3, degraded: false });
    expect(input.value).toBe('');
  });

  it('shows the imported transaction count as a polite status message', () => {
    component.onDrop(dropEvent([pdfFile()]));
    completeImport(42);

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('42 Transaktionen erkannt.');
    // AC: Der Erfolgsfall bleibt eine höfliche Meldung — role="status", nicht "alert".
    expect(notice?.getAttribute('role')).toBe('status');
  });

  it('explains the zero-transaction case instead of showing a bare count', () => {
    // BE-PDF-05: erkannter Auszug ohne Buchungen liefert 202 {total: 0}. Die Meldung muss
    // einordnen (Konto ohne Bewegung vs. falsches PDF), bleibt aber ein freundliches info-Notice.
    // Kein Status-Poll: Es gibt keinen Lauf zu verfolgen.
    component.onDrop(dropEvent([pdfFile()]));
    completeImport(0);

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Keine Transaktionen erkannt.');
    expect(notice?.textContent).toContain('prüfe, ob es das richtige PDF ist');
    expect(notice?.getAttribute('role')).toBe('status');
  });

  it('uses the singular for exactly one imported transaction', () => {
    component.onDrop(dropEvent([pdfFile()]));
    completeImport(1);

    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      '1 Transaktion erkannt.',
    );
  });

  it('rejects a non-PDF file without calling the backend', () => {
    component.onDrop(dropEvent([new File(['a'], 'notizen.txt', { type: 'text/plain' })]));
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Nur PDF-Dateien werden unterstützt.');
    expect(component.uploading()).toBe(false);
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Nur PDF-Dateien werden unterstützt.');
    // AC: Auch Client-Validierungsfehler sind Fehler — rot und assertiv statt amber (#28).
    expect(notice?.getAttribute('role')).toBe('alert');
    expect(notice?.classList.contains('notice--error')).toBe(true);
    httpMock.expectNone('/api/import/pdf');
  });

  it('accepts a dropped file without MIME type when the name ends in .pdf', () => {
    component.onDrop(dropEvent([new File(['%PDF-1.4'], 'Kontoauszug.PDF', { type: '' })]));

    completeImport(1);
    expect(component.importOutcome()).toEqual({ kind: 'success', count: 1, degraded: false });
  });

  it('rejects a file larger than 10 MB without calling the backend', () => {
    const oversized = pdfFile();
    Object.defineProperty(oversized, 'size', { value: 10 * 1024 * 1024 + 1 });

    component.onDrop(dropEvent([oversized]));

    expect(component.errorMessage()).toBe('Maximale Dateigrösse: 10 MB');
    httpMock.expectNone('/api/import/pdf');
  });

  it('rejects a drop of multiple files', () => {
    component.onDrop(dropEvent([pdfFile('januar.pdf'), pdfFile('februar.pdf')]));

    expect(component.errorMessage()).toBe('Bitte lade nur eine Datei aufs Mal hoch.');
    httpMock.expectNone('/api/import/pdf');
  });

  it('shows the password message for a 400 with reason PASSWORD_PROTECTED', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/api/import/pdf')
      .flush({ reason: 'PASSWORD_PROTECTED' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Das PDF ist passwortgeschützt.');
    // AC: Fehlerfälle sind assertiv — role="alert" und rote error-Variante.
    expect(notice?.getAttribute('role')).toBe('alert');
    expect(notice?.classList.contains('notice--error')).toBe(true);
  });

  it('shows the format message for a 400 with reason UNSUPPORTED_FORMAT', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/api/import/pdf')
      .flush({ reason: 'UNSUPPORTED_FORMAT' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      'Das PDF konnte nicht als Kontoauszug gelesen werden.',
    );
  });

  it('shows the missing-text-layer message for a 400 with reason MISSING_TEXT_LAYER', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/api/import/pdf')
      .flush({ reason: 'MISSING_TEXT_LAYER' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Das PDF enthält keinen Text');
    expect(notice?.getAttribute('role')).toBe('alert');
    expect(notice?.classList.contains('notice--error')).toBe(true);
  });

  it('falls back to the format message for a 400 without a reason body', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock.expectOne('/api/import/pdf').flush(null, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      'Das PDF konnte nicht als Kontoauszug gelesen werden.',
    );
  });

  it('shows the timeout message with a retry hint for a 408', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/api/import/pdf')
      .flush(null, { status: 408, statusText: 'Request Timeout' });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain(
      'Der Import hat zu lange gedauert und wurde abgebrochen. Bitte versuche es erneut.',
    );
    expect(notice?.getAttribute('role')).toBe('alert');
  });

  it('shows the oversize message for a 413', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/api/import/pdf')
      .flush(null, { status: 413, statusText: 'Payload Too Large' });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Das PDF ist zu gross');
    expect(notice?.getAttribute('role')).toBe('alert');
  });

  it('stops the spinner and shows a generic error for other failures', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/api/import/pdf')
      .flush(null, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.uploading()).toBe(false);
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Der Import ist fehlgeschlagen');
    expect(notice?.getAttribute('role')).toBe('alert');
  });

  it('opens the duplicate dialog instead of an error message on a 409', () => {
    component.onDrop(dropEvent([pdfFile('juli.pdf')]));

    httpMock.expectOne('/api/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    // AC 1: der Dialog erscheint …
    const dialog = fixture.nativeElement.querySelector('app-modal [role="dialog"]');
    expect(dialog).not.toBeNull();
    expect(dialog.textContent).toContain('juli.pdf');
    // … und der Fehler landet nicht zusätzlich als Meldung.
    expect(component.importOutcome()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-notice')).toBeNull();
    expect(component.uploading()).toBe(false);
  });

  it('closes the dialog without importing when the user cancels', () => {
    component.onDrop(dropEvent([pdfFile()]));
    httpMock.expectOne('/api/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    clickModalButton('Abbrechen');
    fixture.detectChanges();

    // AC 2: kein zweiter Request, Dialog weg …
    httpMock.expectNone('/api/import/pdf');
    expect(fixture.nativeElement.querySelector('app-modal')).toBeNull();
    // … und die Erklärung bleibt stehen, ohne den für ein Duplikat falschen Retry-Rat.
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Dieser Kontoauszug wurde bereits importiert.');
    expect(notice?.textContent).not.toContain('versuche es erneut');
  });

  it('repeats the upload with force=true when the user confirms', () => {
    const file = pdfFile();
    component.onDrop(dropEvent([file]));
    httpMock.expectOne('/api/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    clickModalButton('Trotzdem importieren');
    fixture.detectChanges();

    // AC 3: derselbe Upload noch einmal, diesmal mit Force-Flag.
    const req = httpMock.expectOne((r) => r.url === '/api/import/pdf');
    expect(req.request.params.get('force')).toBe('true');
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({ jobId: JOB_ID, total: 28 });
    vi.advanceTimersByTime(1);
    httpMock.expectOne(`/api/import/${JOB_ID}/status`).flush({
      status: 'DONE',
      total: 28,
      processed: 28,
      degraded: false,
    });
    fixture.detectChanges();

    expect(component.importOutcome()).toEqual({ kind: 'success', count: 28, degraded: false });
    expect(fixture.nativeElement.querySelector('app-modal')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      '28 Transaktionen erkannt.',
    );
  });

  it('closes a stale dialog when the next file is selected', () => {
    component.onDrop(dropEvent([pdfFile('juli.pdf')]));
    httpMock.expectOne('/api/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    component.onDrop(dropEvent([pdfFile('august.pdf')]));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-modal')).toBeNull();
    completeImport(5);
  });

  it('ignores a drop while an upload is already running', () => {
    component.onDrop(dropEvent([pdfFile()]));
    component.onDrop(dropEvent([pdfFile()]));

    completeImport(1);
    expect(component.importOutcome()).toEqual({ kind: 'success', count: 1, degraded: false });
  });

  it('marks the dropzone while a file hovers over it and clears the mark on leave', () => {
    component.onDragOver({ preventDefault: () => undefined } as unknown as DragEvent);
    expect(component.dragActive()).toBe(true);

    component.onDragLeave();
    expect(component.dragActive()).toBe(false);
  });
});
