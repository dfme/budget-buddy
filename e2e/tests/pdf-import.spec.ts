import { join } from "node:path";

import { expect, test } from "../fixtures/auth.fixture";

/**
 * E2E-Abdeckung der Must-Have-Story US-04 «Kontoauszug als PDF hochladen» (E2E-PDF-01).
 *
 * Ein Happy Path und ein Fehlerpfad — die in CLAUDE.md («Testing: Frameworks») vorgeschriebene
 * Menge, und zwar pro Story, nicht pro Issue. US-04 besteht aus acht Issues (#13, #17, #18, #27,
 * #28, #29, #83, #95, #292); die beiden Fälle gehören deshalb hierher und nicht in einen
 * Feature-PR. Der Happy Path wächst mit der Story mit — FE-PDF-04 (#292) hat ihn von der
 * Kategorie-Übersicht auf den Import-Screen zurückgeholt, statt dafür ein eigenes E2E-Issue
 * aufzumachen: Er fasst dieselbe DOM an, und die E2E-Abdeckung wird pro Story erfasst.
 *
 * Einstieg über `authenticatedPage`: `/import` liegt hinter `authGuard` UND `onboardingGuard`,
 * die Fixture erledigt beides über die API (siehe `fixtures/auth.fixture.ts`).
 */
test.describe("PDF-Import", () => {
  /**
   * Synthetischer Auszug im generischen Raiffeisen-Layout, das `SwissBankStatementParser` als
   * Fallback parst: `Saldovortrag` als Startsaldo, danach `dd.MM.yyyy`-Zeilen mit Betrag und
   * laufendem Saldo. Fünf Buchungen aus Juni 2025.
   *
   * Die Datei ist bewusst unkomprimiert: reines ASCII, das `cat`, jeder Editor und `git diff`
   * lesbar ausgeben — bei einer Fixture, deren Kerneigenschaft «enthält keine echten Kontodaten»
   * ist, wäre ein Binär-Blob die falsche Ablageform.
   *
   * Nur im PR-Review auf GitHub greift das nicht: dort steht «Binary files differ», weil GitHub
   * nach der Endung klassifiziert. Ein `.gitattributes` mit gesetztem `diff`-Attribut ändert das
   * nicht — nachgemessen an einem Commit, der nur diese Datei anfasst (Files-API weiterhin
   * `has_patch: false`). Wer den Inhalt prüfen will, tut das an der ausgecheckten Datei.
   */
  const FIXTURE_PDF = join(
    __dirname,
    "..",
    "fixtures",
    "pdf",
    "kontoauszug-synthetisch.pdf",
  );

  /** Die fünf Buchungen der Fixture — Grundlage der erwarteten Erfolgsmeldung. */
  const FIXTURE_TRANSACTION_COUNT = 5;

  /**
   * Wartezeit auf das Ergebnis-Banner. Seit ADR-14 (BE-PDF-09) läuft die Kategorisierung als
   * Hintergrund-Job, den das Frontend pollt: Der Upload-Request selbst ist nach dem Parsen
   * (~2s) durch, das Banner erscheint erst nach dem Job. Gewartet wird deshalb weiterhin
   * grosszügig — der Watchdog des Jobs steht auf 300s, in der Testinstanz ohne
   * `ANTHROPIC_API_KEY` dauert die Kategorisierung aber Millisekunden.
   *
   * Ein knapperer Wert würde einen legitim langsamen Import als Testfehler ausweisen, während
   * das Backend noch innerhalb seiner eigenen Grenze arbeitet.
   */
  const IMPORT_RESULT_TIMEOUT_MS = 60_000;

  test("Happy Path: Upload meldet die Anzahl und listet die importierten Buchungen", async ({
    authenticatedPage: page,
  }) => {
    await page.goto("/import");
    await expect(page.getByRole("heading", { name: "Import" })).toBeVisible();

    // Der File-Input ist `hidden` (der sichtbare Weg ist der Button darüber, der ihn klickt).
    // `setInputFiles` braucht keine Sichtbarkeit — Playwright setzt die Dateien direkt am
    // Element, statt einen nativen Dateidialog zu bedienen, den es gar nicht steuern könnte.
    await page.locator('input[type="file"]').setInputFiles(FIXTURE_PDF);

    // Der Erfolg meldet sich als `variant="info"` und damit höflich (role="status") — ein
    // gelungener Import soll den Screenreader nicht unterbrechen (`notice.ts`).
    const success = page.locator('app-notice.notice--info[role="status"]');
    // Der Text wird am `.notice__body` geprüft, nicht am Host: app-notice rendert seit
    // FE-UI-07 ein eigenes Icon, das in den textContent des Hosts mit einflösse.
    const successText = success.locator(".notice__body");
    // Der Import läuft über zwei Stufen (ADR-14): Upload-Request mit Parsing, danach der
    // Kategorisierungs-Job, den das Frontend pollt. Das Banner erscheint erst am Ende.
    await expect(success).toBeVisible({ timeout: IMPORT_RESULT_TIMEOUT_MS });
    await expect(successText).toHaveText(
      `${FIXTURE_TRANSACTION_COUNT} Transaktionen erkannt.`,
    );

    // Gegenprobe zur Zahl im Banner: die stammt direkt aus der HTTP-Response. Dass die Buchungen
    // wirklich persistiert sind, zeigt erst ein zweiter Endpoint — seit FE-PDF-04 ist das
    // `GET /api/import/{jobId}/transactions`, das der Import-Screen selbst abfragt und als Liste
    // rendert. Der frühere Umweg über `/categories?month=2025-06` hatte genau diesen Zweck und
    // ist damit hinfällig: Derselbe Beweis steht jetzt auf der Seite, die gerade geprüft wird.
    const rows = page.locator(".imported__row");
    await expect(rows).toHaveCount(FIXTURE_TRANSACTION_COUNT);

    // Jede Zeile trägt das Korrektur-Dropdown mit den 13 Kategorien aus `shared/category.ts`
    // (FE-CAT-03). Welche Kategorie vorausgewählt ist, ist bewusst nicht Gegenstand: ohne
    // ANTHROPIC_API_KEY fällt in der Testinstanz alles Unbekannte auf `Sonstiges` zurück
    // (`AnthropicProperties`), und der Rest hängt an den Seed-Daten aus Migration V04.
    const firstCategory = rows.first().locator(".imported__category select");
    await expect(firstCategory).toBeVisible();
    await expect(firstCategory.locator("option")).toHaveCount(13);
  });

  test("Fehlerpfad: unlesbares PDF meldet einen Fehler und keinen Erfolg", async ({
    authenticatedPage: page,
  }) => {
    await page.goto("/import");

    // Bewusst Müll-Bytes unter einem .pdf-Namen statt einer .txt-Datei: eine .txt würde schon
    // `PdfUpload.isPdf()` im Browser abweisen und das Backend nie erreichen — dieser Fall ist
    // als Vitest-Unit-Test abgedeckt (`pdf-upload.spec.ts`). Erst so läuft die ganze Kette:
    // Client-Validierung passiert, `Loader.loadPDF()` scheitert, `PdfParseException` →
    // 400 mit reason UNSUPPORTED_FORMAT → Meldung aus `PdfUpload.importErrorMessage`.
    await page.locator('input[type="file"]').setInputFiles({
      name: "kaputt.pdf",
      mimeType: "application/pdf",
      buffer: Buffer.from(
        "Das hier ist kein PDF, sondern schlichter Text.",
        "utf-8",
      ),
    });

    // `variant="error"` ist ein Angular-Input und im DOM unsichtbar; seine beiden Abdrücke sind
    // die Host-Bindings aus `notice.ts`: role="alert" (assertiv) und die Klasse notice--error.
    // Beide zu prüfen ist genauer als `getByRole('alert')` allein.
    const failure = page.locator('app-notice.notice--error[role="alert"]');
    await expect(failure).toBeVisible({ timeout: IMPORT_RESULT_TIMEOUT_MS });
    // Wie oben: der Text hängt am `.notice__body`, das Icon am Host daneben.
    await expect(failure.locator(".notice__body")).toHaveText(
      "Das PDF konnte nicht als Kontoauszug gelesen werden. Bitte lade den Original-Kontoauszug deiner Bank hoch.",
    );

    // Kein Erfolgszustand daneben: ein Fehler, der die Erfolgsmeldung stehen liesse, wäre für
    // den User schlimmer als gar keine Meldung.
    await expect(page.locator("app-notice.notice--info")).toHaveCount(0);
  });
});
