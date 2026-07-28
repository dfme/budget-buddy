import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { ImportResponse } from './import-response.model';
import { PdfImportService } from './pdf-import.service';

describe('PdfImportService', () => {
  let service: PdfImportService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PdfImportService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts the file as multipart form data to /import/pdf', () => {
    const file = new File(['%PDF-1.4'], 'kontoauszug.pdf', { type: 'application/pdf' });
    let received: ImportResponse | undefined;
    service.importPdf(file).subscribe((response) => (received = response));

    const req = httpMock.expectOne('/import/pdf');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    expect((req.request.body as FormData).get('file')).toBe(file);
    req.flush({ count: 42 });

    expect(received).toEqual({ count: 42 });
  });
});
