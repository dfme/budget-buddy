import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ImportJobStatusResponse, ImportStartedResponse } from './import-response.model';
import { ImportPollTimeoutError, PdfImportService } from './pdf-import.service';

/** Antwort des Status-Endpoints mit sinnvollen Defaults. */
function jobStatus(patch: Partial<ImportJobStatusResponse> = {}): ImportJobStatusResponse {
  return { status: 'RUNNING', total: 3, processed: 0, degraded: false, ...patch };
}

describe('PdfImportService', () => {
  let service: PdfImportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    // Vitest-Faketimer statt fakeAsync/tick: Das Projekt läuft zoneless, zone-testing.js ist
    // deshalb gar nicht geladen.
    vi.useFakeTimers();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PdfImportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    vi.useRealTimers();
  });

  it('posts the file as multipart form data to /api/import/pdf', () => {
    const file = new File(['%PDF-1.4'], 'kontoauszug.pdf', { type: 'application/pdf' });
    let received: ImportStartedResponse | undefined;
    service.importPdf(file).subscribe((response) => (received = response));

    const req = httpMock.expectOne('/api/import/pdf');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({ jobId: 7, total: 42 });

    expect(received).toEqual({ jobId: 7, total: 42 });
  });

  it('sends no force parameter for a regular import', () => {
    const file = new File(['%PDF-1.4'], 'kontoauszug.pdf', { type: 'application/pdf' });
    service.importPdf(file).subscribe();

    const req = httpMock.expectOne('/api/import/pdf');
    expect(req.request.params.has('force')).toBe(false);
    req.flush({ jobId: 7, total: 42 });
  });

  it('sends force=true once the user confirmed the duplicate', () => {
    const file = new File(['%PDF-1.4'], 'kontoauszug.pdf', { type: 'application/pdf' });
    service.importPdf(file, true).subscribe();

    const req = httpMock.expectOne((r) => r.url === '/api/import/pdf');
    expect(req.request.params.get('force')).toBe('true');
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({ jobId: 7, total: 42 });
  });

  it('polls the job status until it reaches a terminal state', () => {
    const seen: ImportJobStatusResponse[] = [];
    service.pollJob(7).subscribe((status) => seen.push(status));

    // Erster Poll läuft sofort (timer startet bei 0) — sonst stünde der Balken bis zum ersten
    // Intervall leer da. Unter Faketimern braucht auch die 0-Verzögerung einen Tick.
    vi.advanceTimersByTime(1);
    httpMock.expectOne('/api/import/7/status').flush(jobStatus({ processed: 1 }));

    vi.advanceTimersByTime(700);
    httpMock.expectOne('/api/import/7/status').flush(jobStatus({ processed: 2 }));

    vi.advanceTimersByTime(700);
    httpMock.expectOne('/api/import/7/status').flush(jobStatus({ status: 'DONE', processed: 3 }));

    // Der Endzustand muss beim Aufrufer ankommen (takeWhile mit inclusive) …
    expect(seen.map((s) => s.processed)).toEqual([1, 2, 3]);
    expect(seen.at(-1)?.status).toBe('DONE');

    // … und danach darf kein weiterer Request mehr rausgehen.
    vi.advanceTimersByTime(2100);
    httpMock.expectNone('/api/import/7/status');
  });

  it('stops polling when the job fails', () => {
    const seen: ImportJobStatusResponse[] = [];
    service.pollJob(7).subscribe((status) => seen.push(status));

    vi.advanceTimersByTime(1);
    httpMock.expectOne('/api/import/7/status').flush(jobStatus({ status: 'FAILED' }));

    expect(seen.at(-1)?.status).toBe('FAILED');
    vi.advanceTimersByTime(2100);
    httpMock.expectNone('/api/import/7/status');
  });

  it('gives up instead of polling forever when a job stays RUNNING', () => {
    // Ein Job kann dauerhaft auf RUNNING stehen bleiben: ImportJobRunner.run() fängt nur
    // RuntimeException (ein Error läuft daran vorbei), und verwaiste Jobs werden beim Start
    // nicht versöhnt — ein Redeploy oder harter Kill lässt die Zeile stehen. Ohne Obergrenze
    // pollte der Client dann unbegrenzt weiter, bei eingefrorenem Balken und ohne Meldung.
    let error: unknown = null;
    let completed = false;
    service.pollJob(7).subscribe({
      error: (e: unknown) => (error = e),
      complete: () => (completed = true),
    });

    // 30 simulierte Minuten, jede Abfrage weiterhin RUNNING.
    for (let elapsed = 0; elapsed < 30 * 60 * 1000 && !error; elapsed += 700) {
      vi.advanceTimersByTime(elapsed === 0 ? 1 : 700);
      for (const request of httpMock.match('/api/import/7/status')) {
        request.flush(jobStatus({ status: 'RUNNING', processed: 1 }));
      }
    }

    expect(error).toBeInstanceOf(ImportPollTimeoutError);
    expect(completed).toBe(false);

    // Nach dem Abbruch geht nichts mehr raus — das ist der Punkt der Übung.
    vi.advanceTimersByTime(60_000);
    httpMock.expectNone('/api/import/7/status');
  });

  it('keeps polling a slow but healthy job well past the first minute', () => {
    // Gegenprobe zur Obergrenze: Sie darf einen ehrlich langsamen Import nicht abschneiden.
    // Der serverseitige Watchdog steht auf 300s; eine Minute muss klar durchgehen.
    let error: unknown = null;
    const seen: ImportJobStatusResponse[] = [];
    service.pollJob(7).subscribe({
      next: (status) => seen.push(status),
      error: (e: unknown) => (error = e),
    });

    for (let elapsed = 0; elapsed < 60_000; elapsed += 700) {
      vi.advanceTimersByTime(elapsed === 0 ? 1 : 700);
      for (const request of httpMock.match('/api/import/7/status')) {
        request.flush(jobStatus({ status: 'RUNNING', processed: 1 }));
      }
    }

    expect(error).toBeNull();
    expect(seen.length).toBeGreaterThan(80);
  });
});
