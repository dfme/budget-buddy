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
    expect(component.importOutcome()).toBe('success');
    expect(fixture.nativeElement.querySelector('.spinner')).toBeNull();
  });

  it('uploads a file selected via the file picker', () => {
    const input = { files: [pdfFile()], value: 'C:\\fakepath\\kontoauszug.pdf' };
    component.onFilePicked({ target: input } as unknown as Event);

    httpMock.expectOne('/import/pdf').flush({ count: 3 });

    expect(component.importOutcome()).toBe('success');
    expect(input.value).toBe('');
  });

  it('rejects a non-PDF file without calling the backend', () => {
    component.onDrop(dropEvent([new File(['a'], 'notizen.txt', { type: 'text/plain' })]));
    fixture.detectChanges();

    expect(component.errorMessage()).toBe('Nur PDF-Dateien werden unterstützt.');
    expect(component.uploading()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      'Nur PDF-Dateien werden unterstützt.',
    );
    httpMock.expectNone('/import/pdf');
  });

  it('accepts a dropped file without MIME type when the name ends in .pdf', () => {
    component.onDrop(dropEvent([new File(['%PDF-1.4'], 'Kontoauszug.PDF', { type: '' })]));

    httpMock.expectOne('/import/pdf').flush({ count: 1 });
    expect(component.importOutcome()).toBe('success');
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

  it('stops the spinner and keeps a generic error outcome when the request fails', () => {
    component.onDrop(dropEvent([pdfFile()]));

    httpMock.expectOne('/import/pdf').flush(null, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(component.uploading()).toBe(false);
    expect(component.importOutcome()).toBe('error');
    expect(fixture.nativeElement.querySelector('app-notice')?.textContent).toContain(
      'Der Import ist fehlgeschlagen',
    );
  });

  it('ignores a drop while an upload is already running', () => {
    component.onDrop(dropEvent([pdfFile()]));
    component.onDrop(dropEvent([pdfFile()]));

    httpMock.expectOne('/import/pdf').flush({ count: 1 });
    expect(component.importOutcome()).toBe('success');
  });

  it('marks the dropzone while a file hovers over it and clears the mark on leave', () => {
    component.onDragOver({ preventDefault: () => undefined } as unknown as DragEvent);
    expect(component.dragActive()).toBe(true);

    component.onDragLeave();
    expect(component.dragActive()).toBe(false);
  });
});
