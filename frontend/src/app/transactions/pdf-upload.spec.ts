import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideLocationMocks } from '@angular/common/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportJobStatusResponse } from './import-response.model';
import { PdfUpload } from './pdf-upload';
import { Transaction } from './transaction.model';

/** Job-ID, die das Backend-Double in allen Tests zurückgibt. */
const JOB_ID = 7;

/**
 * Eine Buchung, wie sie `GET /api/import/{jobId}/transactions` liefert (BE-PDF-14).
 *
 * <p>`category` ist hier nie `null`: Das Backend löst eine fehlende Zuordnung bereits zu
 * `"Sonstiges"` auf (`TransactionResponse.fromResolvingCategory`) — genau deshalb braucht das
 * Dropdown im Frontend keinen Sonderfall für die Vorauswahl.
 */
function transaction(patch: Partial<Transaction> = {}): Transaction {
  return {
    id: 1,
    buchungsdatum: '2025-06-14',
    buchungstext: 'LASTSCHRIFT',
    buchungsdetails: null,
    betrag: 42.5,
    income: false,
    directionUncertain: false,
    category: 'Sonstiges',
    ...patch,
  };
}

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
   * Beantwortet die Abfrage der importierten Buchungen, die seit FE-PDF-04 auf jedes `DONE` mit
   * Buchungen folgt, und den Reload der Glocke, der seit FE-NOTIF-04 ebenfalls auf `DONE` folgt.
   * Ohne sie blieben die Requests offen und `httpMock.verify()` liesse den Test scheitern.
   */
  function flushImportedTransactions(transactions: Transaction[] = []): void {
    httpMock.expectOne(`/api/import/${JOB_ID}/transactions`).flush(transactions);
    httpMock.expectOne('/api/notifications').flush([]);
    fixture.detectChanges();
  }

  /**
   * Beantwortet den Upload, den ersten Status-Poll und — seit FE-PDF-04 — die Abfrage der
   * importierten Buchungen: der vollständige Import (ADR-14). Ohne den zweiten Schritt bliebe
   * die Komponente im Fortschrittszustand stehen, denn `POST /api/import/pdf` meldet seit
   * BE-PDF-09 nur noch den Start.
   *
   * <p>`transactions` ist per Default leer: Die meisten Tests hier prüfen den Ausgang des
   * Imports, nicht die Liste. Wer sie prüft, gibt die Buchungen mit.
   */
  function completeImport(
    total: number,
    patch: Partial<ImportJobStatusResponse> = {},
    transactions: Transaction[] = [],
  ): void {
    httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total });
    if (total === 0) {
      // Erkannter Auszug ohne Buchungen: Es gibt keinen Lauf zu verfolgen (BE-PDF-05).
      fixture.detectChanges();
      return;
    }
    // Unter Faketimern braucht auch die 0-Verzögerung des ersten Polls einen Tick.
    vi.advanceTimersByTime(1);
    const status: ImportJobStatusResponse = {
      status: 'DONE',
      total,
      processed: total,
      degraded: false,
      ...patch,
    };
    httpMock.expectOne(`/api/import/${JOB_ID}/status`).flush(status);
    fixture.detectChanges();
    // Nur ein abgeschlossener Job liefert Buchungen — nach FAILED fragt die Komponente nicht.
    if (status.status === 'DONE') {
      flushImportedTransactions(transactions);
    }
  }

  beforeEach(async () => {
    // Vitest-Faketimer statt fakeAsync/tick: Das Projekt läuft zoneless, zone-testing.js ist
    // deshalb gar nicht geladen. Der Poll-Takt aus PdfImportService hängt an einem Timer.
    vi.useFakeTimers();
    await TestBed.configureTestingModule({
      imports: [PdfUpload],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        // Ein Catch-all ohne Component: die Komponente wird hier von Hand erzeugt, nicht vom
        // Router gerendert. Gebraucht wird er trotzdem, weil die Komponente seit FE-NOTIF-05
        // `?job=` liest und nach dem Upload relativ zur aktuellen Route schreibt — ohne Route
        // bräche die Navigation ab. provideLocationMocks() hält das im Speicher statt in der
        // echten History. Dasselbe Setup wie in category-overview.spec.ts.
        provideRouter([{ path: '**', children: [] }]),
        provideLocationMocks(),
      ],
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

    flushImportedTransactions();

    expect(component.uploading()).toBe(false);
    expect(component.importOutcome()).toEqual({ kind: 'success', count: 42, degraded: false });
    expect(fixture.nativeElement.querySelector('app-meter')).toBeNull();
  });

  // FE-NOTIF-04 (#336): die Abo-Benachrichtigung des Imports soll sofort in der Glocke stehen,
  // nicht erst nach der nächsten Navigation.
  it('reloads the notification bell once the job is DONE', () => {
    component.onDrop(dropEvent([pdfFile()]));
    fixture.detectChanges();
    httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 3 });
    vi.advanceTimersByTime(1);
    httpMock.expectOne(`/api/import/${JOB_ID}/status`).flush({
      status: 'DONE',
      total: 3,
      processed: 3,
      degraded: false,
    });
    httpMock.expectOne(`/api/import/${JOB_ID}/transactions`).flush([]);

    const reload = httpMock.expectOne('/api/notifications');
    expect(reload.request.method).toBe('GET');
    reload.flush([]);
  });

  it('keeps the import a success when the bell reload fails', () => {
    component.onDrop(dropEvent([pdfFile()]));
    fixture.detectChanges();
    httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 3 });
    vi.advanceTimersByTime(1);
    httpMock.expectOne(`/api/import/${JOB_ID}/status`).flush({
      status: 'DONE',
      total: 3,
      processed: 3,
      degraded: false,
    });
    httpMock.expectOne(`/api/import/${JOB_ID}/transactions`).flush([]);
    httpMock
      .expectOne('/api/notifications')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(component.importOutcome()).toEqual({ kind: 'success', count: 3, degraded: false });
  });

  it('does not reload the notification bell when the job fails', () => {
    component.onDrop(dropEvent([pdfFile()]));
    fixture.detectChanges();
    completeImport(12, { status: 'FAILED' });

    httpMock.expectNone('/api/notifications');
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
    flushImportedTransactions();

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

  /**
   * Die Liste der importierten Buchungen (FE-PDF-04). Sie löst den Umweg über die
   * Kategorie-Übersicht ab: Wer soeben importiert hat, sieht hier, wie kategorisiert wurde, und
   * kann es an Ort und Stelle richtigstellen.
   */
  describe('imported transaction list', () => {
    /** Öffnet das Kategorie-Dropdown der n-ten Zeile. */
    function categorySelect(index = 0): HTMLSelectElement {
      const selects = Array.from<HTMLSelectElement>(
        fixture.nativeElement.querySelectorAll('.imported__category select'),
      );
      const select = selects[index];
      if (!select) {
        throw new Error(`Keine Zeile mit Index ${index} — gefunden: ${selects.length}`);
      }
      return select;
    }

    /** Löst ein change-Event auf dem Dropdown aus, wie es der Browser bei einer Auswahl täte. */
    function chooseCategory(select: HTMLSelectElement, label: string): void {
      select.value = label;
      select.dispatchEvent(new Event('change'));
      fixture.detectChanges();
    }

    function rows(): NodeListOf<HTMLElement> {
      return fixture.nativeElement.querySelectorAll('.imported__row');
    }

    it('shows one row per imported transaction with date, text, amount and category', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(2, {}, [
        transaction({ id: 11, buchungsdatum: '2025-06-14', betrag: 42.5, category: 'Sonstiges' }),
        transaction({
          id: 12,
          buchungsdatum: '2025-06-02',
          buchungstext: 'TWINT',
          betrag: 18,
          category: 'Restaurant',
        }),
      ]);

      expect(rows()).toHaveLength(2);
      const first = rows()[0].textContent ?? '';
      expect(first).toContain('14.06.2025');
      expect(first).toContain('LASTSCHRIFT');
      expect(first).toContain('42.50');
      expect(categorySelect(0).value).toBe('Sonstiges');
      expect(categorySelect(1).value).toBe('Restaurant');
    });

    /**
     * Die zweite Zeile ist der Grund, warum die Liste überhaupt brauchbar ist: `buchungstext`
     * trägt bei PostFinance nur die Zahlungsart, die Gegenpartei steht in `buchungsdetails`
     * (BE-PDF-07). Ohne Details entfällt sie ersatzlos — ein Platzhalter behauptete, es gebe
     * keine Gegenpartei.
     */
    it('renders the counterparty as a second line and omits it when absent', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(2, {}, [
        transaction({ id: 11, buchungsdetails: 'ZALANDO SE\nRECHNUNG 4711' }),
        transaction({ id: 12, buchungsdetails: null }),
      ]);

      expect(rows()[0].querySelector('.imported__details')?.textContent).toContain('ZALANDO SE');
      expect(rows()[1].querySelector('.imported__details')).toBeNull();
    });

    /**
     * Die Richtung steht in `income`, nicht im Betrag: `GET /api/import/{jobId}/transactions`
     * liefert den ganzen Import inklusive Gutschriften, `betrag` aber immer als positive
     * Magnitude. Ohne Vorzeichen wäre eine Rückerstattung von einer Belastung nicht zu
     * unterscheiden — und Farbe allein trüge die Information nicht (Review zu #305).
     */
    it('distinguishes a credit from a debit by its sign', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(2, {}, [
        transaction({ id: 11, betrag: 42.5, income: false }),
        transaction({ id: 12, betrag: 120, income: true }),
      ]);

      const amounts = fixture.nativeElement.querySelectorAll('.imported__amount');
      expect(amounts[0].textContent).toContain('−42.50');
      expect(amounts[1].textContent).toContain('+120.00');
      // Nicht nur Farbe: Screenreader hören die Richtung als Wort.
      expect(amounts[0].getAttribute('aria-label')).toBe('minus 42.50 Franken');
      expect(amounts[1].getAttribute('aria-label')).toBe('plus 120.00 Franken');
    });

    /** AC 6: Der Nullfall zeigt nur die bestehende Meldung — und fragt gar nicht erst nach. */
    it('shows no list and requests no transactions for a zero-transaction import', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(0);

      expect(fixture.nativeElement.querySelector('.imported')).toBeNull();
      expect(component.importedTransactions()).toBeNull();
      // `completeImport` beantwortet für total === 0 weder Status noch Buchungen; ein trotzdem
      // abgesetzter Request bliebe offen und liesse `httpMock.verify()` scheitern.
    });

    /** AC 5: Der degradierte Fall behält seinen Hinweis — die Liste kommt dazu, nicht dafür. */
    it('keeps the degraded hint next to the list', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(108, { degraded: true }, [transaction({ id: 11 })]);

      expect(fixture.nativeElement.textContent).toContain('108 Transaktionen erkannt.');
      expect(fixture.nativeElement.textContent).toContain(
        'konnte nicht automatisch kategorisiert werden',
      );
      expect(rows()).toHaveLength(1);
    });

    /** AC 2 und 4: dieselben 17 Kategorien wie in FE-CAT-03, «Sonstiges» vorausgewählt. */
    it('offers the 17 categories with Sonstiges preselected for an unassigned booking', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(1, {}, [transaction({ id: 11, category: 'Sonstiges' })]);

      const options = Array.from(categorySelect().options).map((o) => o.value);
      expect(options).toHaveLength(17);
      expect(options).toContain('Lebensmittel');
      expect(categorySelect().value).toBe('Sonstiges');
    });

    /** AC 3, Erfolgsfall: der neue Wert steht sofort da, der PUT bestätigt ihn nur. */
    it('saves a category correction via PUT and keeps the new value', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(1, {}, [transaction({ id: 11, category: 'Sonstiges' })]);

      chooseCategory(categorySelect(), 'Lebensmittel');

      // Optimistisch: Der Wert steht im DOM, bevor der Server geantwortet hat.
      expect(categorySelect().value).toBe('Lebensmittel');

      const req = httpMock.expectOne('/api/transactions/11/category');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ category: 'Lebensmittel' });
      req.flush(transaction({ id: 11, category: 'Lebensmittel' }));
      fixture.detectChanges();

      expect(categorySelect().value).toBe('Lebensmittel');
      expect(component.saveErrorMessage()).toBeNull();
    });

    /** AC 3, Fehlerfall: Die Anzeige behauptet nie einen Stand, den der Server nicht hat. */
    it('rolls the category back and reports the failure when the PUT fails', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(1, {}, [transaction({ id: 11, category: 'Sonstiges' })]);

      chooseCategory(categorySelect(), 'Lebensmittel');
      httpMock
        .expectOne('/api/transactions/11/category')
        .flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(categorySelect().value).toBe('Sonstiges');
      expect(component.importedTransactions()?.[0].category).toBe('Sonstiges');
      expect(fixture.nativeElement.textContent).toContain(
        'Die Kategorie konnte nicht gespeichert werden.',
      );
    });

    it('sends no request when the chosen category is the current one', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(1, {}, [transaction({ id: 11, category: 'Sonstiges' })]);

      chooseCategory(categorySelect(), 'Sonstiges');

      httpMock.expectNone('/api/transactions/11/category');
    });

    /**
     * Ein gescheiterter Listen-Request ist kein gescheiterter Import: Die Buchungen sind
     * gespeichert, nur ihre Anzeige fehlt. Die Erfolgsmeldung bleibt deshalb stehen.
     */
    it('reports a failed list request without retracting the success message', () => {
      component.onDrop(dropEvent([pdfFile()]));
      httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 3 });
      vi.advanceTimersByTime(1);
      httpMock
        .expectOne(`/api/import/${JOB_ID}/status`)
        .flush({ status: 'DONE', total: 3, processed: 3, degraded: false });
      fixture.detectChanges();
      httpMock
        .expectOne(`/api/import/${JOB_ID}/transactions`)
        .flush(null, { status: 500, statusText: 'Server Error' });
      httpMock.expectOne('/api/notifications').flush([]);
      fixture.detectChanges();

      expect(component.importOutcome()).toEqual({ kind: 'success', count: 3, degraded: false });
      expect(fixture.nativeElement.textContent).toContain('3 Transaktionen erkannt.');
      expect(fixture.nativeElement.textContent).toContain(
        'Die importierten Buchungen konnten nicht geladen werden.',
      );
      expect(fixture.nativeElement.querySelector('.imported')).toBeNull();
    });

    /** AC 7: Die Liste des vorigen Imports gehört zu einem anderen Job und verschwindet. */
    it('clears the previous list when the next upload starts', () => {
      component.onDrop(dropEvent([pdfFile('juni.pdf')]));
      completeImport(1, {}, [transaction({ id: 11 })]);
      expect(rows()).toHaveLength(1);

      component.onDrop(dropEvent([pdfFile('juli.pdf')]));
      fixture.detectChanges();

      expect(component.importedTransactions()).toBeNull();
      expect(fixture.nativeElement.querySelector('.imported')).toBeNull();

      completeImport(2, {}, [transaction({ id: 21 }), transaction({ id: 22 })]);
      expect(rows()).toHaveLength(2);
    });

    /**
     * Der Force-Pfad läuft an `selectFile` vorbei — `confirmDuplicateImport` springt direkt in
     * `upload()`. Das Aufräumen sitzt deshalb dort und nicht in der Dateiauswahl.
     */
    it('clears the previous list on a forced re-import as well', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(1, {}, [transaction({ id: 11 })]);
      expect(rows()).toHaveLength(1);

      component.onDrop(dropEvent([pdfFile()]));
      httpMock.expectOne('/api/import/pdf').flush(null, { status: 409, statusText: 'Conflict' });
      fixture.detectChanges();
      clickModalButton('Trotzdem importieren');
      fixture.detectChanges();

      expect(component.importedTransactions()).toBeNull();

      httpMock.expectOne((r) => r.url === '/api/import/pdf').flush({ jobId: JOB_ID, total: 1 });
      vi.advanceTimersByTime(1);
      httpMock
        .expectOne(`/api/import/${JOB_ID}/status`)
        .flush({ status: 'DONE', total: 1, processed: 1, degraded: false });
      fixture.detectChanges();
      flushImportedTransactions([transaction({ id: 31 })]);

      expect(rows()).toHaveLength(1);
    });

    /**
     * Eine client-seitig abgelehnte Datei erreicht `upload()` nie. Die Liste muss trotzdem weg:
     * Die Erfolgsmeldung darüber verschwindet in `selectFile`, und eine Liste ohne ihre Meldung
     * sähe aus, als gehörte sie zu der eben zurückgewiesenen Datei.
     */
    it('clears the previous list when the next file is rejected client-side', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(1, {}, [transaction({ id: 11 })]);
      expect(rows()).toHaveLength(1);

      component.onDrop(dropEvent([new File(['x'], 'notizen.txt', { type: 'text/plain' })]));
      fixture.detectChanges();

      expect(component.errorMessage()).toBe('Nur PDF-Dateien werden unterstützt.');
      expect(component.importedTransactions()).toBeNull();
      expect(fixture.nativeElement.querySelector('.imported')).toBeNull();
    });

    /**
     * Zwischen Anfrage und Antwort liegt ein Zeitfenster: Wählt der Nutzer darin eine neue Datei,
     * gehört die eintreffende Liste zu einem Import, den er bereits hinter sich gelassen hat.
     * Ohne Guard stünde sie danach unter dem Fortschrittsbalken des neuen Uploads (Review zu
     * #305).
     */
    it('discards a transaction list that arrives after the next upload has started', () => {
      component.onDrop(dropEvent([pdfFile('juni.pdf')]));
      httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 1 });
      vi.advanceTimersByTime(1);
      httpMock
        .expectOne(`/api/import/${JOB_ID}/status`)
        .flush({ status: 'DONE', total: 1, processed: 1, degraded: false });
      fixture.detectChanges();
      // Die Buchungen bleiben offen — genau der Zustand, in dem der Nutzer weitermacht.
      const stale = httpMock.expectOne(`/api/import/${JOB_ID}/transactions`);
      httpMock.expectOne('/api/notifications').flush([]);

      component.onDrop(dropEvent([pdfFile('juli.pdf')]));
      fixture.detectChanges();
      stale.flush([transaction({ id: 11 })]);
      fixture.detectChanges();

      expect(component.importedTransactions()).toBeNull();
      expect(fixture.nativeElement.querySelector('.imported')).toBeNull();

      // Der neue Import füllt die Liste weiterhin — der Guard sperrt nur die verspätete Antwort.
      completeImport(2, {}, [transaction({ id: 21 }), transaction({ id: 22 })]);
      expect(rows()).toHaveLength(2);
    });

    /** Gleiches Fenster, anderer Ausgang: auch die Fehlermeldung gehört zum alten Import. */
    it('discards a failed list request that belongs to a superseded import', () => {
      component.onDrop(dropEvent([pdfFile('juni.pdf')]));
      httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 1 });
      vi.advanceTimersByTime(1);
      httpMock
        .expectOne(`/api/import/${JOB_ID}/status`)
        .flush({ status: 'DONE', total: 1, processed: 1, degraded: false });
      fixture.detectChanges();
      const stale = httpMock.expectOne(`/api/import/${JOB_ID}/transactions`);
      httpMock.expectOne('/api/notifications').flush([]);

      component.onDrop(dropEvent([new File(['x'], 'notizen.txt', { type: 'text/plain' })]));
      fixture.detectChanges();
      stale.flush(null, { status: 500, statusText: 'Server Error' });
      fixture.detectChanges();

      expect(component.listErrorMessage()).toBeNull();
      expect(fixture.nativeElement.textContent).not.toContain(
        'Die importierten Buchungen konnten nicht geladen werden.',
      );
    });

    it('requests no transactions when the background job failed', () => {
      component.onDrop(dropEvent([pdfFile()]));
      completeImport(12, { status: 'FAILED', processed: 5 });

      expect(component.importedTransactions()).toBeNull();
      expect(fixture.nativeElement.querySelector('.imported')).toBeNull();
    });
  });

  // FE-NOTIF-05 (#348): Die Glocke führt bei einer Import-Benachrichtigung nach
  // `/import?job=<id>`; die Seite nimmt den Job über den bestehenden Poll-Pfad auf.
  describe('deep link via ?job', () => {
    /**
     * Räumt die Instanz aus dem `beforeEach` ab und baut eine neue unter dieser Adresse auf —
     * der Deep-Link wird beim Aufbau gelesen, ein nachträgliches Setzen träfe die falsche Phase.
     * Reihenfolge wie in `category-overview.spec.ts`: erst zerstören, dann navigieren, sonst
     * weckt die Navigation die alte Subscription.
     */
    async function recreate(queryParams: Record<string, string>): Promise<void> {
      fixture.destroy();
      await TestBed.inject(Router).navigate([], { queryParams });
      fixture = TestBed.createComponent(PdfUpload);
      component = fixture.componentInstance;
      fixture.detectChanges();
    }

    /** Beantwortet den ersten (und bei einem Endzustand einzigen) Status-Poll. */
    function flushStatus(patch: Partial<ImportJobStatusResponse>, jobId = JOB_ID): void {
      vi.advanceTimersByTime(1);
      httpMock
        .expectOne(`/api/import/${jobId}/status`)
        .flush({ status: 'DONE', total: 5, processed: 5, degraded: false, ...patch });
      fixture.detectChanges();
    }

    function dropzoneButton(): HTMLButtonElement | null {
      return fixture.nativeElement.querySelector('.dropzone button');
    }

    // AC 1: Erfolgsmeldung und Übersicht, wie nach einem frischen Upload — ohne Upload.
    it('shows the success message and the transaction list for a DONE job', async () => {
      await recreate({ job: String(JOB_ID) });
      httpMock.expectNone('/api/import/pdf');
      // Bis der erste Poll antwortet, ist die Dropzone gesperrt — sonst könnte in dieser Lücke
      // ein Upload starten, dessen Poll mit diesem um die Signals stritte.
      expect(component.uploading()).toBe(true);

      flushStatus({ total: 2, processed: 2 });
      flushImportedTransactions([transaction({ id: 1 }), transaction({ id: 2, betrag: 3 })]);

      expect(component.uploading()).toBe(false);
      expect(component.importOutcome()).toEqual({ kind: 'success', count: 2, degraded: false });
      expect(fixture.nativeElement.querySelectorAll('.imported__row').length).toBe(2);
      expect(fixture.nativeElement.querySelector('.imported__category select')).not.toBeNull();
      expect(TestBed.inject(Location).path()).toContain(`job=${JOB_ID}`);
    });

    // AC 1: der degradierte Fall trägt seinen bestehenden Hinweis.
    it('keeps the degraded hint for a degraded job', async () => {
      await recreate({ job: String(JOB_ID) });

      flushStatus({ degraded: true });
      flushImportedTransactions([transaction()]);

      expect(component.importOutcome()).toEqual({ kind: 'success', count: 5, degraded: true });
      expect(fixture.nativeElement.querySelector('app-notice.notice--info').textContent).toContain(
        'konnte nicht automatisch kategorisiert werden',
      );
    });

    // AC 2: die Fehlermeldung des Fehlschlags, Dropzone frei für einen neuen Versuch.
    it('shows the job failure message for a FAILED job and keeps the dropzone usable', async () => {
      await recreate({ job: String(JOB_ID) });

      flushStatus({ status: 'FAILED', processed: 2 });

      expect(component.uploading()).toBe(false);
      expect(component.importOutcome()).toEqual({
        kind: 'error',
        message: 'Der Import ist fehlgeschlagen — bitte versuche es erneut.',
      });
      expect(dropzoneButton()).not.toBeNull();
      httpMock.expectNone(`/api/import/${JOB_ID}/transactions`);
    });

    // AC 3: unbekannt oder fremd — das Backend antwortet in beiden Fällen mit 404, die Seite
    // erklärt das statt eines generischen Fehlers. Die Mandantentrennung bleibt im Backend.
    it('explains a 404 as no longer available and keeps the dropzone usable', async () => {
      await recreate({ job: '999' });

      vi.advanceTimersByTime(1);
      httpMock
        .expectOne('/api/import/999/status')
        .flush(null, { status: 404, statusText: 'Not Found' });
      fixture.detectChanges();

      expect(component.uploading()).toBe(false);
      expect(component.importOutcome()).toEqual({
        kind: 'error',
        message: 'Dieser Import ist nicht mehr abrufbar.',
      });
      expect(dropzoneButton()).not.toBeNull();
    });

    // AC 4: keine positive Ganzzahl → wie ohne Parameter, und die Adresse wird bereinigt.
    it.each(['abc', '0', '-1', '1.5', '', '1e3', ' 7'])(
      'ignores an invalid job parameter %j and strips it from the URL',
      async (raw) => {
        await recreate({ job: raw });
        // Die Bereinigung der Adresse ist eine Navigation; unter Faketimern lässt erst
        // advanceTimersByTimeAsync ihre Mikro- und Makrotasks laufen — whenStable() hinge.
        await vi.advanceTimersByTimeAsync(1);

        expect(component.uploading()).toBe(false);
        expect(component.importOutcome()).toBeNull();
        httpMock.expectNone((req) => req.url.startsWith('/api/import/'));
        expect(TestBed.inject(Location).path()).not.toContain('job=');
      },
    );

    // AC 5: nach dem Upload steht der neue Job in der URL — und das Echo der eigenen Navigation
    // startet den Import nicht neu.
    it('writes the job into the URL after an upload without restarting the poll', async () => {
      component.onDrop(dropEvent([pdfFile()]));
      httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 3 });
      // Lässt die Navigation auf ?job= samt ihrem Echo in queryParamMap durchlaufen — und den
      // timer(0) des Polls.
      await vi.advanceTimersByTimeAsync(1);

      expect(TestBed.inject(Location).path()).toContain(`job=${JOB_ID}`);
      // Der Abdruck eines Neustarts aus dem Echo ist nicht ein zweiter Request — den storniert
      // trackJob beim Wechsel selbst —, sondern der zurückgesetzte Fortschritt: openJob nähme
      // dem Balken seinen Nenner und zeigte wieder den Spinner (Mutationsprobe: ohne die
      // trackedJobId-Wache steht hier null).
      expect(component.progress()).toEqual({ processed: 0, total: 3 });

      httpMock
        .expectOne(`/api/import/${JOB_ID}/status`)
        .flush({ status: 'DONE', total: 3, processed: 3, degraded: false });
      fixture.detectChanges();
      flushImportedTransactions([transaction()]);
      expect(component.importOutcome()).toEqual({ kind: 'success', count: 3, degraded: false });
    });

    it('writes the job into the URL for a zero-transaction upload as well', async () => {
      component.onDrop(dropEvent([pdfFile()]));
      httpMock.expectOne('/api/import/pdf').flush({ jobId: JOB_ID, total: 0 });
      await vi.advanceTimersByTimeAsync(1);

      expect(TestBed.inject(Location).path()).toContain(`job=${JOB_ID}`);
      httpMock.expectNone(`/api/import/${JOB_ID}/status`);
    });

    // Ein Auszug ohne Buchungen wird als DONE mit total 0 persistiert (BE-PDF-05). Über ?job=
    // kommt dieser Fall in trackJob an — anders als nach einem Upload — und darf keine leere
    // Liste erzeugen.
    it('requests no transactions for a DONE job with zero transactions', async () => {
      await recreate({ job: String(JOB_ID) });

      flushStatus({ total: 0, processed: 0 });
      httpMock.expectOne('/api/notifications').flush([]);
      fixture.detectChanges();

      httpMock.expectNone(`/api/import/${JOB_ID}/transactions`);
      expect(component.importedTransactions()).toBeNull();
      expect(fixture.nativeElement.querySelector('app-notice.notice--info').textContent).toContain(
        'Keine Transaktionen erkannt',
      );
    });

    // «Zu untersuchen» aus #348: alte Benachrichtigung zu einem Import, den ein späterer
    // Force-Import ersetzt hat — Status DONE, Buchungen gelöscht. Ein Satz statt einer leeren
    // Liste; die Erfolgsmeldung bleibt stehen.
    it('explains an empty list of a DONE job as replaced by a later import', async () => {
      await recreate({ job: String(JOB_ID) });

      flushStatus({});
      flushImportedTransactions([]);

      expect(component.importOutcome()).toEqual({ kind: 'success', count: 5, degraded: false });
      expect(component.importedTransactions()).toBeNull();
      expect(fixture.nativeElement.querySelector('ul.imported')).toBeNull();
      const notices = Array.from<HTMLElement>(
        fixture.nativeElement.querySelectorAll('app-notice.notice--info'),
      ).map((notice) => notice.textContent);
      expect(notices.some((text) => text?.includes('erneuten Import desselben Kontoauszugs'))).toBe(
        true,
      );
    });

    it('clears the replaced hint when the next upload starts', async () => {
      await recreate({ job: String(JOB_ID) });
      flushStatus({});
      flushImportedTransactions([]);
      expect(component.listReplacedMessage()).not.toBeNull();

      component.onDrop(dropEvent([pdfFile()]));

      expect(component.listReplacedMessage()).toBeNull();
      httpMock.expectOne('/api/import/pdf').flush(null, { status: 500, statusText: 'Error' });
    });

    // Glocke während eines laufenden Imports oder Browser-Zurück auf ein früheres ?job=: der
    // alte Poll muss aufhören, sonst schreiben zwei Polls abwechselnd in dieselben Signals.
    it('stops polling the previous job when another job is opened from the URL', async () => {
      await recreate({ job: String(JOB_ID) });
      flushStatus({ status: 'RUNNING', processed: 1 });
      expect(component.progress()).toEqual({ processed: 1, total: 5 });

      await TestBed.inject(Router).navigate([], { queryParams: { job: '8' } });
      fixture.detectChanges();

      // Job 8 beginnt sofort (timer(0)); sein DONE beendet seinen Poll.
      flushStatus({ total: 1, processed: 1 }, 8);
      httpMock.expectOne('/api/import/8/transactions').flush([transaction()]);
      httpMock.expectOne('/api/notifications').flush([]);
      fixture.detectChanges();

      expect(component.importOutcome()).toEqual({ kind: 'success', count: 1, degraded: false });
      expect(fixture.nativeElement.querySelectorAll('.imported__row').length).toBe(1);

      // Der nächste Takt des alten Polls wäre jetzt fällig — er kommt nicht mehr.
      vi.advanceTimersByTime(700);
      httpMock.expectNone(`/api/import/${JOB_ID}/status`);
      expect(component.importOutcome()).toEqual({ kind: 'success', count: 1, degraded: false });
    });
  });

  it('marks the dropzone while a file hovers over it and clears the mark on leave', () => {
    component.onDragOver({ preventDefault: () => undefined } as unknown as DragEvent);
    expect(component.dragActive()).toBe(true);

    component.onDragLeave();
    expect(component.dragActive()).toBe(false);
  });
});
