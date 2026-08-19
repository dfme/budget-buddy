import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PdfUpload } from './pdf-upload';

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

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PdfUpload],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    fixture = TestBed.createComponent(PdfUpload);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('uploads a dropped PDF and shows the spinner while the request is pending', () => {
    component.onDrop(dropEvent([pdfFile()]));
    fixture.detectChanges();

    expect(component.uploading()).toBe(true);
    expect(fixture.nativeElement.querySelector('.spinner')).not.toBeNull();

    const req = httpMock.expectOne('/import/pdf');
    expect(req.request.method).toBe('POST');
    req.flush({ count: 42 });
    fixture.detectChanges();

    expect(component.uploading()).toBe(false);
    expect(component.importOutcome()).toEqual({ kind: 'success', count: 42 });
    expect(fixture.nativeElement.querySelector('.spinner')).toBeNull();
  });

  it('uploads a file selected via the file picker', () => {
    const input = { files: [pdfFile()], value: 'C:\\fakepath\\kontoauszug.pdf' };
    component.onFilePicked({ target: input } as unknown as Event);

    httpMock.expectOne('/import/pdf').flush({ count: 3 });

    expect(component.importOutcome()).toEqual({ kind: 'success', count: 3 });
    expect(input.value).toBe('');
  });

  it('shows the imported transaction count as a polite status message', () => {
    component.onDrop(dropEvent([pdfFile()]));
    httpMock.expectOne('/import/pdf').flush({ count: 42 });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('42 Transaktionen erkannt.');
    // AC: Der Erfolgsfall bleibt eine höfliche Meldung — role="status", nicht "alert".
    expect(notice?.getAttribute('role')).toBe('status');
  });

  it('explains the zero-transaction case instead of showing a bare count', () => {
    // BE-PDF-05: erkannter Auszug ohne Buchungen liefert 200 {count: 0}. Die Meldung muss
    // einordnen (Konto ohne Bewegung vs. falsches PDF), bleibt aber ein freundliches info-Notice.
    component.onDrop(dropEvent([pdfFile()]));
    httpMock.expectOne('/import/pdf').flush({ count: 0 });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Keine Transaktionen erkannt.');
    expect(notice?.textContent).toContain('prüfe, ob es das richtige PDF ist');
    expect(notice?.getAttribute('role')).toBe('status');
  });

  it('uses the singular for exactly one imported transaction', () => {
    component.onDrop(dropEvent([pdfFile()]));
    httpMock.expectOne('/import/pdf').flush({ count: 1 });
    fixture.detectChanges();

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
    httpMock.expectNone('/import/pdf');
  });

  it('accepts a dropped file without MIME type when the name ends in .pdf', () => {
    component.onDrop(dropEvent([new File(['%PDF-1.4'], 'Kontoauszug.PDF', { type: '' })]));

    httpMock.expectOne('/import/pdf').flush({ count: 1 });
    expect(component.importOutcome()).toEqual({ kind: 'success', count: 1 });
  });

  it('rejects a file larger than 10 MB without calling the backend', () => {
    const oversized = pdfFile();
    Object.defineProperty(oversized, 'size', { value: 10 * 1024 * 1024 + 1 });

    component.onDrop(dropEvent([oversized]));

    expect(component.errorMessage()).toBe('Maximale Dateigrösse: 10 MB');
    httpMock.expectNone('/import/pdf');
  });

  it('rejects a drop of multiple files', () => {
    component.onDrop(dropEvent([pdfFile('januar.pdf'), pdfFile('februar.pdf')]));

    expect(component.errorMessage()).toBe('Bitte lade nur eine Datei aufs Mal hoch.');
    httpMock.expectNone('/import/pdf');
  });

  it('shows the password message for a 400 with reason PASSWORD_PROTECTED', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/import/pdf')
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
      .expectOne('/import/pdf')
      .flush({ reason: 'UNSUPPORTED_FORMAT' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      'Das PDF konnte nicht als Kontoauszug gelesen werden.',
    );
  });

  it('shows the missing-text-layer message for a 400 with reason MISSING_TEXT_LAYER', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/import/pdf')
      .flush({ reason: 'MISSING_TEXT_LAYER' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Das PDF enthält keinen Text');
    expect(notice?.getAttribute('role')).toBe('alert');
    expect(notice?.classList.contains('notice--error')).toBe(true);
  });

  it('falls back to the format message for a 400 without a reason body', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock.expectOne('/import/pdf').flush(null, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      'Das PDF konnte nicht als Kontoauszug gelesen werden.',
    );
  });

  it('shows the timeout message with a retry hint for a 408', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock.expectOne('/import/pdf').flush(null, { status: 408, statusText: 'Request Timeout' });
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
      .expectOne('/import/pdf')
      .flush(null, { status: 413, statusText: 'Payload Too Large' });
    fixture.detectChanges();

    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Das PDF ist zu gross');
    expect(notice?.getAttribute('role')).toBe('alert');
  });

  it('stops the spinner and shows a generic error for other failures', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock
      .expectOne('/import/pdf')
      .flush(null, { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.uploading()).toBe(false);
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Der Import ist fehlgeschlagen');
    expect(notice?.getAttribute('role')).toBe('alert');
  });

  it('opens the duplicate dialog instead of an error message on a 409', () => {
    component.onDrop(dropEvent([pdfFile('juli.pdf')]));

    httpMock.expectOne('/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
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
    httpMock.expectOne('/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    clickModalButton('Abbrechen');
    fixture.detectChanges();

    // AC 2: kein zweiter Request, Dialog weg …
    httpMock.expectNone('/import/pdf');
    expect(fixture.nativeElement.querySelector('app-modal')).toBeNull();
    // … und die Erklärung bleibt stehen, ohne den für ein Duplikat falschen Retry-Rat.
    const notice = fixture.nativeElement.querySelector('app-notice');
    expect(notice?.textContent).toContain('Dieser Kontoauszug wurde bereits importiert.');
    expect(notice?.textContent).not.toContain('versuche es erneut');
  });

  it('repeats the upload with force=true when the user confirms', () => {
    const file = pdfFile();
    component.onDrop(dropEvent([file]));
    httpMock.expectOne('/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    clickModalButton('Trotzdem importieren');
    fixture.detectChanges();

    // AC 3: derselbe Upload noch einmal, diesmal mit Force-Flag.
    const req = httpMock.expectOne((r) => r.url === '/import/pdf');
    expect(req.request.params.get('force')).toBe('true');
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({ count: 28 });
    fixture.detectChanges();

    expect(component.importOutcome()).toEqual({ kind: 'success', count: 28 });
    expect(fixture.nativeElement.querySelector('app-modal')).toBeNull();
    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      '28 Transaktionen erkannt.',
    );
  });

  it('closes a stale dialog when the next file is selected', () => {
    component.onDrop(dropEvent([pdfFile('juli.pdf')]));
    httpMock.expectOne('/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    component.onDrop(dropEvent([pdfFile('august.pdf')]));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-modal')).toBeNull();
    httpMock.expectOne('/import/pdf').flush({ count: 5 });
  });

  it('ignores a drop while an upload is already running', () => {
    component.onDrop(dropEvent([pdfFile()]));
    component.onDrop(dropEvent([pdfFile()]));

    httpMock.expectOne('/import/pdf').flush({ count: 1 });
    expect(component.importOutcome()).toEqual({ kind: 'success', count: 1 });
  });

  it('marks the dropzone while a file hovers over it and clears the mark on leave', () => {
    component.onDragOver({ preventDefault: () => undefined } as unknown as DragEvent);
    expect(component.dragActive()).toBe(true);

    component.onDragLeave();
    expect(component.dragActive()).toBe(false);
  });
});
