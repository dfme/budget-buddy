# FE-PDF-02 — Ergebnis-Anzeige nach PDF-Import

- **Issue:** #28 — `[FE-PDF-02] Ergebnis-Anzeige nach PDF-Import`
- **Task-ID:** `FE-PDF-02`
- **Branch:** `feature/FE-PDF-02-import-ergebnis-anzeige`
- **Typ:** Feature (US-04)
- **Depends on:** #27 (FE-PDF-01), #18 (BE-PDF-03), #99/#100/#104 (Notice mit `error`-Variante)

## Ausgangslage

`pdf-upload.ts` hält den Import-Ausgang nur generisch (`importOutcome: 'success' | 'error'`),
das Template zeigt drei Zustände über `app-notice` — zwei davon fälschlich in der
Default-Variante `warning` (amber, `role="status"`) statt `error` (rot, `role="alert"`).

**Blockierender Befund aus der Analyse:** Der Backend-Contract (BE-PDF-03) liefert für beide
400-Fälle bewusst **keinen Body** (`PdfImportExceptionHandler.java`) — Passwort- und
Format-Fehler sind auf dem Draht nicht unterscheidbar. Die ACs verlangen aber zwei getrennte
Meldungen.

## Entscheid (mit Team geklärt)

**Backend minimal erweitern:** Die beiden 400-Handler geben künftig einen kleinen JSON-Body
`{"reason": "PASSWORD_PROTECTED" | "UNSUPPORTED_FORMAT"}` zurück. 408/409/413 bleiben body-los
(Status genügt dort). Scope-Erweiterung wird im PR-Body deklariert.

Verworfene Alternativen:
- *Eine kombinierte 400-Meldung (frontend-only):* verwässert zwei ACs zu einer.
- *Eigenes Backend-Issue zuerst:* sauberste Trennung, blockiert aber den Task.

## Betroffene Files

Backend (Scope-Erweiterung):
- `backend/src/main/java/com/budgetbuddy/transaction/dto/ImportErrorResponse.java` — neu
- `backend/src/main/java/com/budgetbuddy/transaction/PdfImportExceptionHandler.java` — 400-Handler mit Body
- `backend/src/main/java/com/budgetbuddy/transaction/PdfImportController.java` — OpenAPI-Schema für 400
- `backend/src/test/java/com/budgetbuddy/transaction/PdfImportControllerIntegrationTest.java` — `jsonPath`-Assertions auf `reason`

Frontend:
- `frontend/src/app/transactions/import-error.model.ts` — neu, spiegelt `ImportErrorResponse.java`
- `frontend/src/app/transactions/pdf-upload.ts` — `importOutcome` als diskriminierte Union, Fehler-Mapping
- `frontend/src/app/transactions/pdf-upload.html` — Count-Anzeige, Varianten-Korrektur
- `frontend/src/app/transactions/pdf-upload.spec.ts` — Tests für alle Meldungs-Zustände + ARIA-Rollen

## Meldungstexte

| Fall | Meldung | Variante |
|---|---|---|
| Erfolg | «{n} Transaktionen erkannt.» / «1 Transaktion erkannt.» | `info`, `role="status"` |
| 400 `PASSWORD_PROTECTED` | «Das PDF ist passwortgeschützt. Bitte entferne das Passwort und lade es erneut hoch.» | `error`, `role="alert"` |
| 400 `UNSUPPORTED_FORMAT` / 400 ohne reason | «Das PDF konnte nicht als Kontoauszug gelesen werden. Bitte lade den Original-Kontoauszug deiner Bank hoch.» | `error` |
| 408 | «Der Import hat zu lange gedauert und wurde abgebrochen. Bitte versuche es erneut.» | `error` |
| Sonstige (inkl. 409 bis FE-PDF-03, 413) | «Der Import ist fehlgeschlagen — bitte versuche es erneut.» | `error` |

## Implementierungsschritte

1. Backend: `ImportErrorResponse`-Record + Reason-Enum; Handler-Umbau; OpenAPI-Annotation
2. Backend: Integrationstests asserten `reason` im 400-Body (Passwort + Format)
3. Frontend: Fehler-Mapping in `pdf-upload.ts`, Success-Count im Signal
4. Frontend: Template — Meldungs-Zustände, alle Fehler auf `variant="error"`
5. Frontend: Spec-Erweiterung
6. `./mvnw package` + `ng build` + `ng test` als DoD-Nachweis

## Test-Strategie

- Backend Integration: beide 400-Fälle mit Status + `reason`-Body
- Frontend Unit (Vitest): fünf Meldungs-Zustände + ARIA-Rollen (`alert` bei Fehlern, `status` bei Erfolg)
- E2E: entfällt — Playwright-Setup (INFRA-14, #91) existiert noch nicht

## Bewusst nicht enthalten

- 409-Duplikat-Dialog → FE-PDF-03 (#29); 409 fällt bis dahin in den generischen Fallback.

## Acceptance Criteria (aus Issue #28)

- [ ] Erfolg: Anzahl importierter Transaktionen wird angezeigt
- [ ] 400 Passwort: verständliche Fehlermeldung
- [ ] 400 Format: verständliche Fehlermeldung
- [ ] 408 Timeout: Timeout-Meldung mit Retry-Hinweis
- [ ] Alle Fehlerfälle nutzen `app-notice variant="error"` — rot (`$c-negative`) und `role="alert"`
- [ ] Der Erfolgsfall bleibt eine höfliche Meldung (`role="status"`)
